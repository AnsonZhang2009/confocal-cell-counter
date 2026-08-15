package org.confocal.cellcounter;

import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Comparator;

import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.FileWidget;

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.Overlay;
import ij.io.FileSaver;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.filter.EDM;
import ij.plugin.filter.ParticleAnalyzer;
import ij.process.AutoThresholder;
import ij.process.ImageConverter;
import ij.process.ImageProcessor;

@Plugin(type = Command.class, menuPath = "Plugins>Confocal Cell Counter")
public class ConfocalCellCounterCommand implements Command {

	@Parameter(label = "Input directory", style = FileWidget.DIRECTORY_STYLE,
		description = "Folder containing the TIFF images to process.")
	private File inputDir;

	@Parameter(label = "Threshold method", choices = {
		"Otsu", "Default", "Huang", "Li", "Mean", "Triangle", "Yen", "Moments" },
		description = "Auto-threshold used before binarization (cells bright on dark background).")
	private String thresholdMethod = "Otsu";

	@Parameter(label = "Min particle size (pixels^2)")
	private double minSize = 0;

	@Parameter(label = "Max particle size (pixels^2)",
		description = "Particles larger than this are ignored.")
	private double maxSize = Double.MAX_VALUE;

	@Parameter(label = "Min circularity", description = "0.0 to 1.0")
	private double minCircularity = 0.0;

	@Parameter(label = "Max circularity", description = "0.0 to 1.0")
	private double maxCircularity = 1.0;

	@Parameter(label = "Exclude particles on edges")
	private boolean excludeOnEdges = false;

	@Parameter(label = "Include holes")
	private boolean includeHoles = true;

	@Parameter
	private LogService log;

	@Override
	public void run() {
		if (inputDir == null || !inputDir.isDirectory()) {
			log.error("Invalid input directory: " + inputDir);
			return;
		}

		File[] files = inputDir.listFiles((dir, name) -> {
			if (name.startsWith(".")) return false;
			File f = new File(dir, name);
			if (f.isDirectory()) return false;
			String lower = name.toLowerCase();
			return lower.endsWith(".tif") || lower.endsWith(".tiff");
		});
		if (files == null || files.length == 0) {
			log.error("No .tif/.tiff files found in " + inputDir.getPath());
			return;
		}
		Arrays.sort(files, Comparator.comparing(File::getName));

		File outDir = new File(inputDir, "processed");
		if (!outDir.exists() && !outDir.mkdirs()) {
			log.error("Could not create output directory: " + outDir.getPath());
			return;
		}

		AutoThresholder.Method method;
		try {
			method = AutoThresholder.Method.valueOf(thresholdMethod);
		}
		catch (IllegalArgumentException e) {
			method = AutoThresholder.Method.Otsu;
		}

		PrintWriter summary = null;
		try {
			summary = new PrintWriter(new FileWriter(new File(outDir, "summary.csv")));
			summary.println("image,count,mean_area");
		}
		catch (IOException e) {
			log.error("Could not create summary.csv: " + e.getMessage());
		}

		boolean oldBlackBackground = Prefs.blackBackground;
		Prefs.blackBackground = true;
		int succeeded = 0;
		try {
			for (File file : files) {
				try {
					process(file, outDir, method, summary);
					succeeded++;
				}
				catch (Throwable t) {
					log.error("Failed on " + file.getName() + ": " + t);
				}
			}
		}
		finally {
			Prefs.blackBackground = oldBlackBackground;
			if (summary != null) summary.close();
		}

		log.info("Confocal Cell Counter finished: " + succeeded + "/" + files.length +
			" images processed. Results written to " + outDir.getPath());
	}

	private void process(File file, File outDir, AutoThresholder.Method method, PrintWriter summary)
		throws IOException
	{
		ImagePlus imp = IJ.openImage(file.getPath());
		if (imp == null) {
			log.error("Could not open " + file.getName());
			return;
		}
		String name = file.getName();
		int dot = name.lastIndexOf('.');
		String base = dot > 0 ? name.substring(0, dot) : name;

		try {
			ImagePlus work = imp.duplicate();
			new ImageConverter(work).convertToGray8();
			ImageProcessor ip = work.getProcessor();

			ip.setAutoThreshold(method, true);
			double lower = ip.getMinThreshold();
			ip.resetThreshold();
			if (lower == ImageProcessor.NO_THRESHOLD) lower = 0;
			ip.threshold((int) Math.round(lower));
			new EDM().toWatershed(ip);

			ResultsTable rt = new ResultsTable();
			int options = ParticleAnalyzer.SHOW_OVERLAY_MASKS;
			if (excludeOnEdges) options |= ParticleAnalyzer.EXCLUDE_EDGE_PARTICLES;
			if (includeHoles) options |= ParticleAnalyzer.INCLUDE_HOLES;
			int measurements = Measurements.AREA | Measurements.MEAN | Measurements.MIN_MAX |
				Measurements.CENTROID | Measurements.CENTER_OF_MASS | Measurements.PERIMETER |
				Measurements.SHAPE_DESCRIPTORS | Measurements.FERET;
			ParticleAnalyzer pa = new ParticleAnalyzer(options, measurements, rt,
				minSize, maxSize, minCircularity, maxCircularity);
			pa.setHideOutputImage(true);
			pa.analyze(work, ip);

			int count = rt.getCounter();

			Overlay overlay = work.getOverlay();
			if (overlay != null) {
				overlay.drawLabels(true);
				overlay.setLabelColor(Color.WHITE);
				overlay.setLabelFont(new Font("SansSerif", Font.BOLD, 12));
				overlay.drawBackgrounds(true);
			}

			ImagePlus overlayImp = imp.duplicate();
			if (overlay != null) overlayImp.setOverlay(overlay);
			ImagePlus flat = overlayImp.flatten();
			new FileSaver(flat).saveAsPng(new File(outDir, base + "_overlay.png").getPath());
			flat.close();
			overlayImp.close();

			rt.saveAs(new File(outDir, base + "_results.csv").getPath());

			if (summary != null) {
				double meanArea = meanColumn(rt, "Area");
				summary.println(base + "," + count + "," +
					(Double.isNaN(meanArea) ? "" : meanArea));
				summary.flush();
			}

			log.info(base + ": " + count + " particles");
		}
		finally {
			imp.close();
		}
	}

	private double meanColumn(ResultsTable rt, String heading) {
		int index = rt.getColumnIndex(heading);
		if (index < 0) return Double.NaN;
		float[] column = rt.getColumn(index);
		if (column == null || column.length == 0) return Double.NaN;
		double sum = 0;
		for (float v : column)
			sum += v;
		return sum / column.length;
	}
}
