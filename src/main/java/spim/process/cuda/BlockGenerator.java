package spim.process.cuda;

public interface BlockGenerator< T extends Block >
{
	/**
	 * Divides an image into blocks
	 * 
	 * @param imgSize - the size of the image
	 * @param kernelSize - the size of the kernel (has to be odd!)
	 * @return
	 */
	public T[] divideIntoBlocks( final long[] imgSize, final long[] kernelSize );
}
