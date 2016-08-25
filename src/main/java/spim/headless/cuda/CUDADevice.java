package spim.headless.cuda;

import com.sun.jna.Native;
import mpicbg.spim.io.IOFunctions;
import spim.process.cuda.CUDAFourierConvolution;
import spim.process.cuda.CUDASeparableConvolution;
import spim.process.cuda.CUDAStandardFunctions;

import java.util.ArrayList;

/**
 * Created by moon on 7/1/15.
 */
public class CUDADevice
{
	public static CUDASeparableConvolution separableConvolution;
	public static CUDAFourierConvolution fourierConvolution;

	public static ArrayList< spim.process.cuda.CUDADevice > getFFTCudaList(String path)
	{
		fourierConvolution = (CUDAFourierConvolution ) Native.loadLibrary( path, CUDAFourierConvolution.class );

		if ( fourierConvolution == null )
		{
			IOFunctions.println( "Cannot load CUDA JNA library." );
			return null;
		}

		return getDevices(fourierConvolution);
	}

	public static ArrayList< spim.process.cuda.CUDADevice > getSeparableCudaList(String path)
	{
		separableConvolution = (CUDASeparableConvolution ) Native.loadLibrary( path, CUDASeparableConvolution.class );

		if ( separableConvolution == null )
		{
			IOFunctions.println( "Cannot load CUDA JNA library." );
			return null;
		}

		return getDevices(separableConvolution);
	}

	private static ArrayList< spim.process.cuda.CUDADevice > getDevices( final CUDAStandardFunctions cuda )
	{
		final int numDevices = cuda.getNumDevicesCUDA();

		if ( numDevices == -1 )
		{
			IOFunctions.println( "Querying CUDA devices crashed, no devices available." );
			return null;
		}
		else if ( numDevices == 0 )
		{
			IOFunctions.println( "No CUDA devices detected." );
			return null;
		}

		final ArrayList< spim.process.cuda.CUDADevice > deviceList = new ArrayList< spim.process.cuda.CUDADevice >();

		final byte[] name = new byte[ 256 ];
		int highestComputeCapability = 0;
		long highestMemory = 0;

		int highestComputeCapabilityDevice = -1;

		for ( int i = 0; i < numDevices; ++i )
		{
			cuda.getNameDeviceCUDA( i, name );

			String deviceName = "";

			for ( final byte b : name )
				if ( b != 0 )
					deviceName += (char)b;

			deviceName.trim();

			final long mem = cuda.getMemDeviceCUDA( i );
			long freeMem;

			try
			{
				freeMem = cuda.getFreeMemDeviceCUDA( i );
			}
			catch (UnsatisfiedLinkError e )
			{
				IOFunctions.println( "Using an outdated version of the CUDA libs, cannot query free memory. Assuming total memory." );
				freeMem = mem;
			}
			final int majorVersion = cuda.getCUDAcomputeCapabilityMajorVersion( i );
			final int minorVersion = cuda.getCUDAcomputeCapabilityMinorVersion( i );
			final int compCap =  10 * majorVersion + minorVersion;

			if ( compCap > highestComputeCapability )
			{
				highestComputeCapability = compCap;
				highestComputeCapabilityDevice = i;
			}

			if ( mem > highestMemory )
				highestMemory = mem;

			deviceList.add( new spim.process.cuda.CUDADevice( i, deviceName, mem, freeMem, majorVersion, minorVersion ) );
		}

		return deviceList;
	}
}
