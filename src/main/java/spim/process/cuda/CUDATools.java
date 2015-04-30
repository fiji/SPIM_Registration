package spim.process.cuda;

import ij.gui.GenericDialog;

import java.util.ArrayList;
import java.util.Collections;

import mpicbg.spim.io.IOFunctions;
import spim.fiji.plugin.util.GenericDialogAppender;

public class CUDATools
{
	/**
	 * 0 ... n == index for i'th CUDA device
	 */
	public static ArrayList< Boolean > deviceChoice = null;
	public static int standardDevice = -1;

	/**
	 * @param cuda
	 * @param askForMultipleDevices
	 * @return - a list of CUDA device Id's to be used
	 */
	public static ArrayList< CUDADevice > queryCUDADetails( final CUDAStandardFunctions cuda, final boolean askForMultipleDevices )
	{
		return queryCUDADetails( cuda, askForMultipleDevices, null );
	}
	
	/**
	 * @param cuda
	 * @param askForMultipleDevices
	 * @param additionalQueries
	 * @return - a list of CUDA device Id's to be used
	 */
	public static ArrayList< CUDADevice > queryCUDADetails( final CUDAStandardFunctions cuda, final boolean askForMultipleDevices, final GenericDialogAppender additionalQueries )
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

		//
		// get the ID's and functionality of the CUDA GPU's
		//
		final CUDADevice[] deviceList = new CUDADevice[ numDevices ];

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

			deviceList[ i ] = new CUDADevice( i, deviceName, mem, freeMem, majorVersion, minorVersion );
		}
		
		// get the CPU specs
		// final String cpuSpecs = "CPU (" + Threads.numThreads() + " cores, " + Runtime.getRuntime().maxMemory()/(1024*1024) + " MB RAM available)";

		final ArrayList< CUDADevice > selectedDevices = new ArrayList< CUDADevice >();

		// if we use blocks, it makes sense to run more than one device
		if ( askForMultipleDevices )
		{
			// make a list where all are checked if there is no previous selection
			if ( deviceChoice == null || deviceChoice.size() != deviceList.length ) //+ 1 )
			{
				deviceChoice = new ArrayList<Boolean>( deviceList.length + 1 );
				for ( int i = 0; i < deviceList.length; ++i )
					deviceChoice.add( true );
				
				// CPU is by default not checked
				deviceChoice.add( false );
			}
			
//			final GenericDialog gdCUDA = new GenericDialog( "Choose CUDA/CPUs devices to use" );
//
//			for ( int i = 0; i < deviceList.length; ++i )
//				gdCUDA.addCheckbox( "GPU_" + (i+1) + " of " + deviceList.length  + ": " + deviceList[ i ], deviceChoice.get( i ) );

//			if ( additionalQueries != null )
//				additionalQueries.addQuery( gdCUDA );

//			gdCUDA.showDialog();
//
//			if ( gdCUDA.wasCanceled() )
//				return null;

			// check all CUDA devices
			for ( int i = 0; i < deviceList.length; ++i )
			{
//				if( gdCUDA.getNextBoolean() )
//				{
					selectedDevices.add( deviceList[ i ] );
					deviceChoice.set( i , true );
//				}
//				else
//				{
//					deviceChoice.set( i , false );
//				}
			}
			
//			if ( additionalQueries != null )
//				if ( !additionalQueries.parseDialog( gdCUDA ) )
//					return null;

			if ( selectedDevices.size() == 0 )
			{
				IOFunctions.println( "You selected no device, quitting." );
				return null;
			}
		}
		else
		{
			// only choose one device to run everything at once				
//			final GenericDialog gdCUDA = new GenericDialog( "Choose CUDA device" );

			if ( standardDevice < 0 || standardDevice >= deviceList.length )
				standardDevice = highestComputeCapabilityDevice;

//			final String desc[] = new String[ deviceList.length ];
//			for ( int i = 0; i < deviceList.length; ++i )
//				desc[ i ] = "GPU " + (i+1) + " of " + deviceList.length  + ": " + deviceList[ i ];
//
//			gdCUDA.addChoice( "Device", desc, desc[ standardDevice ] );
//
//			if ( additionalQueries != null )
//				additionalQueries.addQuery( gdCUDA );
//
//			gdCUDA.showDialog();
//
//			if ( gdCUDA.wasCanceled() )
//				return null;

//			selectedDevices.add( deviceList[ standardDevice = gdCUDA.getNextChoiceIndex() ] );

			selectedDevices.add( deviceList[ standardDevice ] );

//			if ( additionalQueries != null )
//				if ( !additionalQueries.parseDialog( gdCUDA ) )
//					return null;
		}

		Collections.sort( selectedDevices );

		for ( final CUDADevice dev : selectedDevices )
			IOFunctions.println( "Using device " + dev );

		return selectedDevices;
	}
}
