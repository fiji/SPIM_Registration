package spim.process.deconvolution2.iteration;

import java.util.concurrent.ExecutorService;

import net.imglib2.img.ImgFactory;
import net.imglib2.type.numeric.real.FloatType;

public class ComputeBlockThreadCPUFactory implements ComputeBlockThreadFactory
{
	final ExecutorService service;
	final float minValue;
	final float lambda;
	final int[] blockSize;
	final ImgFactory< FloatType > blockFactory;
	
	public ComputeBlockThreadCPUFactory(
			final ExecutorService service,
			final float minValue,
			final float lambda,
			final int[] blockSize,
			final ImgFactory< FloatType > blockFactory )
	{
		this.service = service;
		this.minValue = minValue;
		this.lambda = lambda;
		this.blockSize = blockSize.clone();
		this.blockFactory = blockFactory;
	}

	@Override
	public ComputeBlockThread create( final int id )
	{
		return new ComputeBlockThreadCPU( service, minValue, lambda, id, blockSize, blockFactory );
	}

	@Override
	public int numParallelBlocks() { return 1; }
}
