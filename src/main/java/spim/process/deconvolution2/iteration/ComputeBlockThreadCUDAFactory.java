package spim.process.deconvolution2.iteration;

import java.util.HashMap;
import java.util.concurrent.ExecutorService;

import spim.process.cuda.CUDAFourierConvolution;

public class ComputeBlockThreadCUDAFactory implements ComputeBlockThreadFactory
{
	final ExecutorService service;
	final float minValue;
	final float lambda;
	final int[] blockSize;
	final CUDAFourierConvolution cuda;
	final HashMap< Integer, Integer > idToCudaDeviceId;

	public ComputeBlockThreadCUDAFactory(
			final ExecutorService service,
			final float minValue,
			final float lambda,
			final int[] blockSize,
			final CUDAFourierConvolution cuda,
			final HashMap< Integer, Integer > idToCudaDeviceId )
	{
		this.service = service;
		this.minValue = minValue;
		this.lambda = lambda;
		this.blockSize = blockSize.clone();
		this.cuda = cuda;
		this.idToCudaDeviceId = idToCudaDeviceId;
	}

	@Override
	public ComputeBlockThread create( final int id )
	{
		return new ComputeBlockThreadCUDA( service, minValue, lambda, id, blockSize, cuda, idToCudaDeviceId.get( id ) );
	}

	@Override
	public int numParallelBlocks() { return idToCudaDeviceId.keySet().size(); }
}
