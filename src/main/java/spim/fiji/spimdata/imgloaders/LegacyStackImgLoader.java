/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2007 - 2017 Fiji developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */
package spim.fiji.spimdata.imgloaders;

import java.io.File;

import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.IntegerPattern;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.type.NativeType;
import spim.fiji.datasetmanager.StackList;


public abstract class LegacyStackImgLoader extends AbstractImgFactoryImgLoader
{
	protected File path = null;
	protected String fileNamePattern = null;
	
	protected String replaceTimepoints, replaceChannels, replaceIlluminations, replaceAngles;
	protected int numDigitsTimepoints, numDigitsChannels, numDigitsIlluminations, numDigitsAngles;
	protected int layoutTP, layoutChannels, layoutIllum, layoutAngles; // 0 == one, 1 == one per file, 2 == all in one file
		
	protected AbstractSequenceDescription< ?, ?, ? > sequenceDescription;

	public File getPath() { return path; }
	public String getFileNamePattern() { return fileNamePattern; }
	public int getLayoutTimePoints() { return layoutTP; }
	public int getLayoutChannels() { return layoutChannels; }
	public int getLayoutIlluminations() { return layoutIllum; }
	public int getLayoutAngles() { return layoutAngles; }
	
	protected < T extends NativeType< T > > Img< T > instantiateImg( final long[] dim, final T type )
	{
		Img< T > img;
		
		try
		{
			img = getImgFactory().imgFactory( type ).create( dim, type );
		}
		catch ( Exception e1 )
		{
			try
			{
				img = new CellImgFactory< T >( 256 ).create( dim, type );
			}
			catch ( Exception e2 )
			{
				img = null;
			}
		}
		
		return img;
	}

	protected File getFile( final ViewId view )
	{
		final TimePoint tp = sequenceDescription.getTimePoints().getTimePoints().get( view.getTimePointId() );
		final BasicViewSetup  vs = sequenceDescription.getViewSetups().get( view.getViewSetupId() );

		final String timepoint = tp.getName();
		final String angle = vs.getAttribute( Angle.class ).getName();
		final String channel = vs.getAttribute( Channel.class ).getName();
		final String illum = vs.getAttribute( Illumination.class ).getName();

		final String[] fileName = StackList.getFileNamesFor( fileNamePattern, replaceTimepoints, replaceChannels,
				replaceIlluminations, replaceAngles, timepoint, channel, illum, angle,
				numDigitsTimepoints, numDigitsChannels, numDigitsIlluminations, numDigitsAngles );

		// check which of them exists and return it
		for ( final String fn : fileName )
		{
			final File f = new File( path, fn );
			
			if ( f.exists() )
				return f;
			else
				IOFunctions.printlnSafe( "File '" + f.getAbsolutePath() + "' does not exist." );
		}

		IOFunctions.printlnSafe( "Could not find file for tp=" + timepoint + ", angle=" + angle + ", channel=" + channel + ", ill=" + illum );

		return null;
	}

	/**
	 * For a local initialization without the XML
	 * 
	 * @param path
	 * @param fileNamePattern
	 * @param imgFactory
	 * @param layoutTP - 0 == one, 1 == one per file, 2 == all in one file
	 * @param layoutChannels - 0 == one, 1 == one per file, 2 == all in one file
	 * @param layoutIllum - 0 == one, 1 == one per file, 2 == all in one file
	 * @param layoutAngles - 0 == one, 1 == one per file, 2 == all in one file
	 */
	public LegacyStackImgLoader(
			final File path, final String fileNamePattern, final ImgFactory< ? extends NativeType< ? > > imgFactory,
			final int layoutTP, final int layoutChannels, final int layoutIllum, final int layoutAngles,
			final AbstractSequenceDescription< ?, ?, ? > sequenceDescription )
	{
		super();
		this.path = path;
		this.fileNamePattern = fileNamePattern;
		this.layoutTP = layoutTP;
		this.layoutChannels = layoutChannels;
		this.layoutIllum = layoutIllum;
		this.layoutAngles = layoutAngles;
		this.sequenceDescription = sequenceDescription;
		
		this.init( imgFactory );
	}

	protected void init( final ImgFactory< ? extends NativeType< ? > > imgFactory )
	{
		setImgFactory( imgFactory );
		
		replaceTimepoints = replaceChannels = replaceIlluminations = replaceAngles = null;
		numDigitsTimepoints = numDigitsChannels = numDigitsIlluminations = numDigitsAngles = -1;
		
		replaceTimepoints = IntegerPattern.getReplaceString( fileNamePattern, StackList.TIMEPOINT_PATTERN );
		replaceChannels = IntegerPattern.getReplaceString( fileNamePattern, StackList.CHANNEL_PATTERN );
		replaceIlluminations = IntegerPattern.getReplaceString( fileNamePattern, StackList.ILLUMINATION_PATTERN );
		replaceAngles = IntegerPattern.getReplaceString( fileNamePattern, StackList.ANGLE_PATTERN );

		if ( replaceTimepoints != null )
			numDigitsTimepoints = replaceTimepoints.length() - 2;

		if ( replaceChannels != null )
			numDigitsChannels = replaceChannels.length() - 2;
		
		if ( replaceIlluminations != null )
			numDigitsIlluminations = replaceIlluminations.length() - 2;
		
		if ( replaceAngles != null )
			numDigitsAngles = replaceAngles.length() - 2;
		/*
		IOFunctions.printlnSafe( replaceTimepoints );
		IOFunctions.printlnSafe( replaceChannels );
		IOFunctions.printlnSafe( replaceIlluminations );
		IOFunctions.printlnSafe( replaceAngles );

		IOFunctions.printlnSafe( layoutTP );
		IOFunctions.printlnSafe( layoutChannels );
		IOFunctions.printlnSafe( layoutIllum );
		IOFunctions.printlnSafe( layoutAngles );

		IOFunctions.printlnSafe( path );
		IOFunctions.printlnSafe( fileNamePattern );
		*/
	}
}
