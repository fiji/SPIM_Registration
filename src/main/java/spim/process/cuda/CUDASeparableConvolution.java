package spim.process.cuda;

public interface CUDASeparableConvolution extends CUDAStandardFunctions
{
	// extern "C" bool convolve_31( float *image, float *kernelX, float *kernelY, float *kernelZ, int imageW, int imageH, int imageD, bool convolveX, bool convolveY, bool convolveZ, int devCUDA );

	// extern "C" int multipleOfX_31();
	// extern "C" int multipleOfY_31();
	// extern "C" int multipleOfZ_31();

	// In-place convolution with a maximal kernel diameter of 31
	public boolean convolve_31( float[] image, float[] kernelX, float[] kernelY, float[] kernelZ, int imageW, int imageH, int imageD, boolean convolveX, boolean convolveY, boolean convolveZ, int devCUDA );

	/**
	 * @return - multiple of which number the dimension has to be to not fail
	 */
	public int multipleOfX_31();
	/**
	 * @return - multiple of which number the dimension has to be to not fail
	 */
	public int multipleOfY_31();
	/**
	 * @return - multiple of which number the dimension has to be to not fail
	 */
	public int multipleOfZ_31();
}
