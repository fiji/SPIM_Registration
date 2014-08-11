package spim.process.cuda;

public class CUDADevice implements Comparable< CUDADevice >
{
	final String name;
	final long mem;
	final int id, majorComputeVersion, minorComputeVersion;

	public CUDADevice( final int id, final String name, final long mem, final int majorComputeVersion, final int minorComputeVersion )
	{
		this.id = id;
		this.name = name;
		this.mem = mem;
		this.majorComputeVersion = majorComputeVersion;
		this.minorComputeVersion = minorComputeVersion;
	}

	public int getDeviceId() { return id; }
	public String getDeviceName() { return name; }
	public long getDeviceMemory() { return mem; }
	public int getMajorComputeVersion() { return majorComputeVersion; }
	public int getMinorComputeVersion() { return minorComputeVersion; }
	public String getComputeVersion() { return getMajorComputeVersion() + "." + getMinorComputeVersion(); }

	@Override
	public String toString() { return getDeviceName() + " (id=" + getDeviceId() + ", mem=" + getDeviceMemory()/(1024*1024) + "MB, CUDA capability " + getComputeVersion() + ")"; }

	@Override
	public int compareTo( final CUDADevice dev )
	{
		return getDeviceId() - dev.getDeviceId();
	}
}
