package spim.process.cuda;

import ij.gui.GenericDialog;

import java.util.ArrayList;
import java.util.Collections;

import mpicbg.spim.io.IOFunctions;

public class CUDATools
{
	/**
	 * 0 ... n == index for i'th CUDA device
	 * n + 1 == CPU
	 */
	public static ArrayList< Boolean > deviceChoice = null;
	public static int standardDevice = 10000;

	/**
	 * @param cuda
	 * @param askForMultipleDevices
	 * @return - a list of CUDA device Id's to be used
	 */
	public static ArrayList< Integer > queryCUDADetails( final CUDAStandardFunctions cuda, final boolean askForMultipleDevices )
	{
		final ArrayList< Integer > deviceList = new ArrayList< Integer >();

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
		final String[] devices = new String[ numDevices ];
		final byte[] name = new byte[ 256 ];
		int highestComputeCapability = 0;
		long highestMemory = 0;

		int highestComputeCapabilityDevice = -1;
		
		for ( int i = 0; i < numDevices; ++i )
		{		
			cuda.getNameDeviceCUDA( i, name );
			
			devices[ i ] = "GPU_" + (i+1) + " of " + numDevices  + ": ";
			for ( final byte b : name )
				if ( b != 0 )
					devices[ i ] = devices[ i ] + (char)b;
			
			devices[ i ].trim();
			
			final long mem = cuda.getMemDeviceCUDA( i );	
			final int compCap =  10 * cuda.getCUDAcomputeCapabilityMajorVersion( i ) + cuda.getCUDAcomputeCapabilityMinorVersion( i );
			
			if ( compCap > highestComputeCapability )
			{
				highestComputeCapability = compCap;
				highestComputeCapabilityDevice = i;
			}
			
			if ( mem > highestMemory )
				highestMemory = mem;
			
			devices[ i ] = devices[ i ] + " (" + mem/(1024*1024) + " MB, CUDA capability " + cuda.getCUDAcomputeCapabilityMajorVersion( i )  + "." + cuda.getCUDAcomputeCapabilityMinorVersion( i ) + ")";
		}
		
		// get the CPU specs
		final String cpuSpecs = "CPU (" + Runtime.getRuntime().availableProcessors() + " cores, " + Runtime.getRuntime().maxMemory()/(1024*1024) + " MB RAM available)";
		
		// if we use blocks, it makes sense to run more than one device
		if ( askForMultipleDevices )
		{
			// make a list where all are checked if there is no previous selection
			if ( deviceChoice == null || deviceChoice.size() != devices.length + 1 )
			{
				deviceChoice = new ArrayList<Boolean>( devices.length + 1 );
				for ( int i = 0; i < devices.length; ++i )
					deviceChoice.add( true );
				
				// CPU is by default not checked
				deviceChoice.add( false );
			}
			
			final GenericDialog gdCUDA = new GenericDialog( "Choose CUDA/CPUs devices to use" );
			
			for ( int i = 0; i < devices.length; ++i )
				gdCUDA.addCheckbox( devices[ i ], deviceChoice.get( i ) );

			gdCUDA.addCheckbox( cpuSpecs, deviceChoice.get( devices.length ) );
			gdCUDA.showDialog();

			if ( gdCUDA.wasCanceled() )
				return null;

			// check all CUDA devices
			for ( int i = 0; i < devices.length; ++i )
			{
				if( gdCUDA.getNextBoolean() )
				{
					deviceList.add( i );
					deviceChoice.set( i , true );
				}
				else
				{
					deviceChoice.set( i , false );
				}
			}
			
			// check the CPUs
			if ( gdCUDA.getNextBoolean() )
			{
				deviceList.add( -1 );
				deviceChoice.set( devices.length , true );
			}
			else
			{
				deviceChoice.set( devices.length , false );				
			}
			
			for ( final int i : deviceList )
			{
				if ( i >= 0 )
					IOFunctions.println( "Using device " + devices[ i ] );
				else if ( i == -1 )
					IOFunctions.println( "Using device " + cpuSpecs );
			}
			
			if ( deviceList.size() == 0 )
			{
				IOFunctions.println( "You selected no device, quitting." );
				return null;
			}
		}
		else
		{
			// only choose one device to run everything at once				
			final GenericDialog gdCUDA = new GenericDialog( "Choose CUDA device" );

			if ( standardDevice >= devices.length )
				standardDevice = highestComputeCapabilityDevice;
			
			gdCUDA.addChoice( "Device", devices, devices[ standardDevice ] );
			
			gdCUDA.showDialog();
		
			if ( gdCUDA.wasCanceled() )
				return null;
			
			deviceList.add( standardDevice = gdCUDA.getNextChoiceIndex() );
			IOFunctions.println( "Using device " + devices[ deviceList.get( 0 ) ] );
		}

		Collections.sort( deviceList );

		return deviceList;
	}
}
