package task;

/**
 * Created by moon on 4/30/15.
 */
public class Pipeline
{
	public void run()
	{
		defineXml();

		resaveHdf5();

		detectInterestPoint();

		register();

		fuse();
	}

	public void defineXml()
	{
		// Input file types:
		//
		// StackListLOCI
		// StackListImageJ
		// MicroManager
		// LightSheetZ1
	}

	public void resaveHdf5()
	{

	}

	public void detectInterestPoint()
	{
		// Algorithms:
		//
		// DifferenceOfMean
		// DifferenceOfGaussian
	}

	public void register()
	{
		// Algorithms:
		//
		// GeometricHashing
		// RGLDM
		// IterativeClosestPoint
	}

	public void fuse()
	{
		// Compute on:
		// CPU
		// GPU

		// Fusion algorithm:
		//
		// EfficientBayesianBased
		// WeightedAverageFusion with FUSEDATA
		// WeightedAverageFusion with INDEPENDENT

		// BoudingBox algorithms:
		//
		// ManualBoundingBox
		// BigDataViewerBoundingBox (x)
		// AutomaticReorientation (?)
		// AutomaticBoundingBox (?)

		// ImageExport options:
		//
		// DisplayImage (x)
		// Save3dTIFF
		// ExportSpimData2TIFF
		// ExportSpimData2HDF5
		// AppendSpimData2
	}
}
