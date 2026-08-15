# Confocal Cell Counter
This is an ImageJ plugin intended to accelerate researchers who need to batch process confocal images for purposes of cell counting. This is an ImageJ [command](https://imagej.net/develop/plugins#Commands) plugin. 
## Usage

## Pipeline Description
The general pipeline is quite simple:
1. Opens dialogue to read user directory
2. `Make Binary`: the images are made binary using the [Otsu](https://en.wikipedia.org/wiki/Otsu%27s_method) auto-thresholding algorithm. Other auto-threshold options are also available. *Future plans include adding manual thresholding.*
3. `Watershed`: this essentially adds a thin layer of separation between cells stuck there, enhancing counting precision.
4. `Analyze Particles`: a dialogue is set to appear that asks the users for particle size directions. `Particle Size` is measured in pixels. *And yes, there are future plans for adding inches.* Other parameters include `Circularity` and whether `Edge Detection` is enabled. 
5. `Add Overlay`: the overlay for cell counting is layered on top of the original image and flattened. 
## Outputs
This `Command` pipeline does not modify your existing files. Instead, outputs are generated in `~/processed`. The overlay and results are placed there, alongside a `summary.csv` file that recapitulates the counts of that batch. 
## License
MIT