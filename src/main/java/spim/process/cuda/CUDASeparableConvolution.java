package spim.process.cuda;

public interface CUDASeparableConvolution extends CUDAStandardFunctions
{
	// extern "C" void convolve_31( float *image, float *kernelX, float *kernelY, float *kernelZ, int imageW, int imageH, int imageD, bool convolveX, bool convolveY, bool convolveZ, int devCUDA );

	// In-place convolution with a maximal kernel diameter of 31
	public void convolve_31( float[] image, float[] kernelX, float[] kernelY, float[] kernelZ, int imageW, int imageH, int imageD, boolean convolveX, boolean convolveY, boolean convolveZ, int devCUDA );
}
