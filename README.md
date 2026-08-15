# Confocal Cell Counter
This is an ImageJ plugin intended to accelerate researchers who need to batch process confocal images for purposes of cell counting. This is an ImageJ [command](https://imagej.net/develop/plugins#Commands) plugin. 
## Usage
1. Download the plugin `.jar` file from releases. 
2. Drag it into the ~/jars folder of Fiji/ImageJ. 
3. Restart the application. 
4. Press `cmd + L` (Mac) or `ctrl + L` (Windows) and search `Confocal Cell Counter`. Click on run. 
5. Select the desired directory. 
6. Change settings in the dialogue menu. 
7. View results in `~/processed`. 
## Pipeline Description
The general pipeline is quite simple:
1. Opens dialogue to read user directory
2. `Make Binary`: the images are made binary using the [Otsu](https://en.wikipedia.org/wiki/Otsu%27s_method) auto-thresholding algorithm. Other auto-threshold options are also available. *Future plans include adding manual thresholding.*
3. `Watershed`: this essentially adds a thin layer of separation between cells stuck there, enhancing counting precision.
4. `Analyze Particles`: a dialogue is set to appear that asks the users for particle size directions. `Particle Size` is measured in pixels. *And yes, there are future plans for adding inches.* Other parameters include `Circularity` and whether `Edge Detection` is enabled. 
5. `Add Overlay`: the overlay for cell counting is layered on top of the original image and flattened. 
## Outputs
This `Command` pipeline does not modify your existing files. Instead, outputs are generated in `~/processed`. The overlay and results are placed there, alongside a `summary.csv` file that recapitulates the counts of that batch. 
## Disclaimer
Note that this project was written with agentic-assistance, used to refractor the original script written by the author into a plugin for ease-of-use. 
## License
MIT