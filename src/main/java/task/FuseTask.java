package task;

/**
 * Headless module for Fuse task
 */
public class FuseTask extends AbstractTask
{
	// Only consider the below cases
	//
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

	// ImageExport options:
	//
	// Save3dTIFF
	// ExportSpimData2TIFF
	// ExportSpimData2HDF5
	// AppendSpimData2

	@Override public void process( String[] args )
	{

	}
}
