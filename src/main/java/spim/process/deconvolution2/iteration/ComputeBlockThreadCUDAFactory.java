package spim.process.deconvolution2.iteration;

import java.util.HashMap;
import java.util.concurrent.ExecutorService;

import spim.process.cuda.CUDADevice;
import spim.process.cuda.CUDAFourierConvolution;

public class ComputeBlockThreadCUDAFactory implements ComputeBlockThreadFactory
{
	final ExecutorService service;
	final float minValue;
	final float lambda;
	final int[] blockSize;
	final CUDAFourierConvolution cuda;
	final HashMap< Integer, CUDADevice > idToCudaDevice;

	public ComputeBlockThreadCUDAFactory(
			final ExecutorService service,
			final float minValue,
			final float lambda,
			final int[] blockSize,
			final CUDAFourierConvolution cuda,
			final HashMap< Integer, CUDADevice > idToCudaDevice )
	{
		this.service = service;
		this.minValue = minValue;
		this.lambda = lambda;
		this.blockSize = blockSize.clone();
		this.cuda = cuda;
		this.idToCudaDevice = idToCudaDevice;
	}

	@Override
	public ComputeBlockThread create( final int id )
	{
		return new ComputeBlockThreadCUDA( service, minValue, lambda, id, blockSize, cuda, idToCudaDevice.get( id ) );
	}

	@Override
	public int numParallelBlocks() { return idToCudaDevice.keySet().size(); }
}
