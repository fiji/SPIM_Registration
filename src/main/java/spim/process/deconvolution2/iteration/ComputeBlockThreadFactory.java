package spim.process.deconvolution2.iteration;

public interface ComputeBlockThreadFactory
{
	public ComputeBlockThread create( final int id );
	public int numParallelBlocks();
}
