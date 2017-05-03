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

import ij.IJ;

import java.io.File;
import java.io.IOException;
import java.util.Date;

import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.ChannelSeparator;
import loci.formats.FormatTools;
import loci.formats.IFormatReader;
import loci.formats.meta.IMetadata;
import loci.formats.services.OMEXMLService;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import spim.fiji.datasetmanager.SlideBook6;
import spim.fiji.datasetmanager.SlideBook6MetaData;

public class LegacySlideBook6ImgLoader extends AbstractImgFactoryImgLoader
{
	final File sldFile;
	final AbstractSequenceDescription<?, ?, ?> sequenceDescription;
	
	// once the metadata is loaded for one view, it is available for all other ones
	SlideBook6MetaData meta;
	boolean isClosed = true;

	public LegacySlideBook6ImgLoader(
			final File sldFile,
			final ImgFactory< ? extends NativeType< ? > > imgFactory,
			final AbstractSequenceDescription< ?, ?, ? > sequenceDescription )
	{
		super();
		this.sldFile = sldFile;
		this.sequenceDescription = sequenceDescription;

		setImgFactory( imgFactory );
	}

	public File getSLDFile() { return sldFile; }

	@Override
	public RandomAccessibleInterval< FloatType > getFloatImage( final ViewId view, final boolean normalize )
	{
		try
		{
			int[] dim = new int[ 3 ];
			float[] voxelSize = new float[ 3 ];
			final Img< FloatType > img = openSLD(new FloatType(), view);

			if ( img == null )
				throw new RuntimeException( "Could not load '" + sldFile + "' viewId=" + view.getViewSetupId() + ", tpId=" + view.getTimePointId() );

			if ( normalize )
				normalize( img );

			return img;
		}
		catch ( Exception e )
		{
			throw new RuntimeException( "Could not load '" + sldFile + "' viewId=" + view.getViewSetupId() + ", tpId=" + view.getTimePointId() + ": " + e );
		}
	}

	@Override
	public RandomAccessibleInterval< UnsignedShortType > getImage( final ViewId view )
	{
		try
		{
			int[] dim = new int[ 3 ];
			float[] voxelSize = new float[ 3 ];
			final Img< UnsignedShortType > img = openSLD(new UnsignedShortType(), view);

			if ( img == null )
				throw new RuntimeException( "Could not load '" + sldFile + "' viewId=" + view.getViewSetupId() + ", tpId=" + view.getTimePointId() );

			return img;
		}
		catch ( Exception e )
		{
			throw new RuntimeException( "Could not load '" + sldFile + "' viewId=" + view.getViewSetupId() + ", tpId=" + view.getTimePointId() + ": " + e );
		}
	}

	@Override
	protected void loadMetaData( final ViewId view )
	{
		IOFunctions.printlnSafe( new Date( System.currentTimeMillis() ) + ": Loading metadata for SlideBook6 imgloader not necessary." );
	}

	@Override
	public void finalize()
	{
		IOFunctions.printlnSafe( "Closing sld: " + sldFile );

		try
		{
			if ( meta != null && meta.getReader() != null )
			{
				meta.getReader().close();
				isClosed = true;
			}
		}
		catch (IOException e) {}
	}

	protected < T extends RealType< T > & NativeType< T > > Img< T > openSLD( final T type, final ViewId view ) throws Exception
	{
		if ( meta == null )
		{
			IOFunctions.printlnSafe( new Date( System.currentTimeMillis() ) + ": Investigating file '" + sldFile.getAbsolutePath() + "' (loading metadata)." );

			meta = new SlideBook6MetaData();

			if ( !meta.loadMetaData( sldFile, true ) )
			{
				IOFunctions.printlnSafe( "Failed to analyze file: '" + sldFile.getAbsolutePath() + "'." );
				meta = null;
				isClosed = true;
				return null;
			}
			else
			{
				isClosed = false;
			}
		}

                /*
		// SlideBook6Reader.dll
		SlideBook6Reader reader = new SlideBook6Reader();

		reader.openFile(sldFile.getPath());
		int position = 0;

		final BasicViewDescription< ? > vd = sequenceDescription.getViewDescriptions().get( view );
		final int i = vd.getViewSetup().getAttribute( Illumination.class ).getId();
		final int c = i / 8; // map from illumination id to SlideBook capture index, up to 8 channels per SlideBook capture
		final int a = vd.getViewSetup().getAttribute( Angle.class ).getId();
		final int w = reader.getNumXColumns(c);
		final int h = reader.getNumYRows(c);
		final int d = reader.getNumZPlanes(c);

		final float voxelSize = reader.getVoxelSize(c);
		final float zSpacing = SlideBook6.getZSpacing(reader, c, position);

		updateMetaDataCache( view, w, h, d, 
					voxelSize, voxelSize, zSpacing );

		// SlideBook6Reader.dll
		reader.closeFile();

		// SlideBook6Reader.dll
		final int width = reader.getNumXColumns(c);
		final int height = reader.getNumYRows(c);
		final int depth = reader.getNumZPlanes(c);
		final int channels = reader.getNumChannels(c);
		final int position = 0;
		voxelSize[ 0 ] = reader.getVoxelSize(c);
		voxelSize[ 1 ] = reader.getVoxelSize(c);
		voxelSize[ 2 ] = SlideBook6.getZSpacing(reader, c, position);

		final int numPx = width * height;

		dim[ 0 ] = width;
		dim[ 1 ] = height;
		dim[ 2 ] = depth;
               */

		final BasicViewDescription< ? > vd = sequenceDescription.getViewDescriptions().get( view );
		final BasicViewSetup vs = vd.getViewSetup();

		final TimePoint t = vd.getTimePoint();
		final Angle a = getAngle( vd );
		final Channel ch = getChannel( vd );
		final Illumination i = getIllumination( vd );
		final int c = i.getId() / 8; // map from illumination id to SlideBook capture index, up to 8 channels per SlideBook capture
		final int[] dim;

		if ( vs.hasSize() )
		{
			dim = new int[ vs.getSize().numDimensions() ];
			for ( int d = 0; d < vs.getSize().numDimensions(); ++d )
				dim[ d ] = (int)vs.getSize().dimension( d );
		}
		else
		{
			dim = meta.imageSize(c);
		}

		final Img< T > img = imgFactory.imgFactory( type ).create( dim, type );

		if ( img == null )
			throw new RuntimeException( "Could not instantiate " + getImgFactory().getClass().getSimpleName() + " for '" + sldFile + "' captureId=" + c + "' viewId=" + view.getViewSetupId() + ", tpId=" + view.getTimePointId() + ", most likely out of memory." );

		IOFunctions.printlnSafe(
				new Date( System.currentTimeMillis() ) + ": Opening '" + sldFile.getName() + "' [" + dim[ 0 ] + "x" + dim[ 1 ] + "x" + dim[ 2 ] +
						" angle=" + a.getName() + " ch=" + ch.getName() + " illum=" + i.getName() + " tp=" + t.getName() + " type=" + FormatTools.getPixelTypeString(FormatTools.UINT16) +
						" img=" + img.getClass().getSimpleName() + "<" + type.getClass().getSimpleName() + ">]" );

		final boolean isLittleEndian = meta.isLittleEndian();
		final boolean isArray = ArrayImg.class.isInstance(img);
		final int pixelType = FormatTools.UINT16;
		final int width = dim[ 0 ];
		final int height = dim[ 1 ];
		final int depth = dim[ 2 ];
		final int numPx = width * height;
		final IFormatReader r;

		// if we already loaded the metadata in this run, use the opened file
		if ( meta.getReader() == null )
			r = LegacySlideBook6ImgLoader.instantiateImageReader();
		else
			r = meta.getReader();

		final byte[] b = new byte[ numPx * FormatTools.getBytesPerPixel(pixelType) ];

		try
		{
			// open the file if not already done
			try
			{
				if ( meta.getReader() == null )
				{
					IOFunctions.printlnSafe( new Date( System.currentTimeMillis() ) + ": Opening '" + sldFile.getName() + "' for reading image data." );
					r.setId( sldFile.getAbsolutePath() );
				}

				// set the right illumination
				r.setSeries( c );
			}
			catch ( IllegalStateException e )
			{
				r.setId( sldFile.getAbsolutePath() );
				r.setSeries( c );
			}

			IOFunctions.printlnSafe(
					new Date( System.currentTimeMillis() ) + ": Reading image data from '" + sldFile.getName() + "' [" + dim[ 0 ] + "x" + dim[ 1 ] + "x" + dim[ 2 ] +
					" angle=" + a.getName() + " ch=" + ch.getName() + " illum=" + i.getName() + " tp=" + t.getName() + " type=" + meta.pixelTypeString() +
					" img=" + img.getClass().getSimpleName() + "<" + type.getClass().getSimpleName() + ">]" );

			// for every illumination, assume each pair of channels represents two angles of the same channel
			int chIndex = (ch.getId()*2) + a.getId();

			for ( int z = 0; z < depth; ++z )
			{
				IJ.showProgress( (double)z / (double)depth );

				final Cursor< T > cursor = Views.iterable( Views.hyperSlice( img, 2, z ) ).localizingCursor();

				r.openBytes( r.getIndex( z, chIndex, t.getId() ), b );

				IOFunctions.printlnSafe("reader.readImagePlaneBuf z = " + z + ", capture = " + c + ", angle = " + a.getId() +
				", channel = " + (chIndex) + ", channels = " + meta.numChannels(c) + ", timepoint = " + t.getId());

				// SlideBook6Reader.dll
				// i = illumination id (SPIMdata) = capture index * 8 (SlideBook)
				// a = angle id (SPIMdata) = channel index  (SlideBook)
				// reader.readImagePlaneBuf(b, c, 0, t.getId(), z, (ch.getId()*2) + a.getId());

				if ( isArray )
					readUnsignedShortsArray( b, cursor, numPx, isLittleEndian );
				else
					readUnsignedShorts( b, cursor, width, isLittleEndian );
			}

			IJ.showProgress( 1 );
		}
		catch ( Exception e )
		{
			IOFunctions.printlnSafe("File '" + sldFile.getAbsolutePath() + "' could not be opened: " + e);
			IOFunctions.printlnSafe( "Stopping" );

			e.printStackTrace();
			return null;
		}

		return img;
	}

	public static final < T extends RealType< T > > void readBytes( final byte[] b, final Cursor< T > cursor, final int width )
	{
		while( cursor.hasNext() )
		{
			cursor.fwd(); // otherwise the position is off below
			cursor.get().setReal( b[ cursor.getIntPosition( 0 ) + cursor.getIntPosition( 1 ) * width ] & 0xff );
		}
	}

	public static final < T extends RealType< T > > void readBytesArray( final byte[] b, final Cursor< T > cursor, final int numPx )
	{
		for ( int i = 0; i < numPx; ++i )
			cursor.next().setReal( b[ i ] & 0xff );
	}

	public static final < T extends RealType< T > > void readUnsignedShorts( final byte[] b, final Cursor< T > cursor, final int width, final boolean isLittleEndian )
	{
		while( cursor.hasNext() )
		{
			cursor.fwd();
			cursor.get().setReal( LegacyStackImgLoaderLOCI.getShortValueInt( b, ( cursor.getIntPosition( 0 ) + cursor.getIntPosition( 1 ) * width ) * 2, isLittleEndian ) );
		}
	}

	public static final < T extends RealType< T > > void readUnsignedShortsArray( final byte[] b, final Cursor< T > cursor, final int numPx, final boolean isLittleEndian )
	{
		for ( int i = 0; i < numPx; ++i )
			cursor.next().setReal( LegacyStackImgLoaderLOCI.getShortValueInt( b, i * 2, isLittleEndian ) );
	}

	public static final < T extends RealType< T > > void readSignedShorts( final byte[] b, final Cursor< T > cursor, final int width, final boolean isLittleEndian )
	{
		while( cursor.hasNext() )
		{
			cursor.fwd();
			cursor.get().setReal( LegacyStackImgLoaderLOCI.getShortValue( b, ( cursor.getIntPosition( 0 ) + cursor.getIntPosition( 1 ) * width ) * 2, isLittleEndian ) );
		}
	}

	public static final < T extends RealType< T > > void readSignedShortsArray( final byte[] b, final Cursor< T > cursor, final int numPx, final boolean isLittleEndian )
	{
		for ( int i = 0; i < numPx; ++i )
			cursor.next().setReal( LegacyStackImgLoaderLOCI.getShortValue( b, i * 2, isLittleEndian ) );
	}

	public static final < T extends RealType< T > > void readUnsignedInts( final byte[] b, final Cursor< T > cursor, final int width, final boolean isLittleEndian )
	{
		while( cursor.hasNext() )
		{
			cursor.fwd();
			cursor.get().setReal( LegacyStackImgLoaderLOCI.getIntValue( b, ( cursor.getIntPosition( 0 ) + cursor.getIntPosition( 1 ) * width ) * 4, isLittleEndian ) );
		}
	}

	public static final < T extends RealType< T > > void readUnsignedIntsArray( final byte[] b, final Cursor< T > cursor, final int numPx, final boolean isLittleEndian )
	{
		for ( int i = 0; i < numPx; ++i )
			cursor.next().setReal( LegacyStackImgLoaderLOCI.getIntValue( b, i * 4, isLittleEndian ) );
	}

	public static final < T extends RealType< T > > void readFloats( final byte[] b, final Cursor< T > cursor, final int width, final boolean isLittleEndian )
	{
		while( cursor.hasNext() )
		{
			cursor.fwd();
			cursor.get().setReal( LegacyStackImgLoaderLOCI.getFloatValue( b, ( cursor.getIntPosition( 0 ) + cursor.getIntPosition( 1 ) * width ) * 4, isLittleEndian ) );
		}
	}

	public static final < T extends RealType< T > > void readFloatsArray( final byte[] b, final Cursor< T > cursor, final int numPx, final boolean isLittleEndian )
	{
		for ( int i = 0; i < numPx; ++i )
			cursor.next().setReal( LegacyStackImgLoaderLOCI.getFloatValue( b, i * 4, isLittleEndian ) );
	}

	public static IFormatReader instantiateImageReader()
	{
		// should I use the ZeissCZIReader here directly?
		return new ChannelSeparator();// new ZeissCZIReader();
	}

	public static boolean createOMEXMLMetadata( final IFormatReader r )
	{
		try
		{
			final ServiceFactory serviceFactory = new ServiceFactory();
			final OMEXMLService service = serviceFactory.getInstance( OMEXMLService.class );
			final IMetadata omexmlMeta = service.createOMEXMLMetadata();
			r.setMetadataStore(omexmlMeta);
		}
		catch (final ServiceException e)
		{
			e.printStackTrace();
			return false;
		}
		catch (final DependencyException e)
		{
			e.printStackTrace();
			return false;
		}

		return true;
	}

	protected static Angle getAngle( final AbstractSequenceDescription< ?, ?, ? > seqDesc, final ViewId view )
	{
		return getAngle( seqDesc.getViewDescriptions().get( view ) );
	}

	protected static Angle getAngle( final BasicViewDescription< ? > vd )
	{
		final BasicViewSetup vs = vd.getViewSetup();
		final Angle angle = vs.getAttribute( Angle.class );

		if ( angle == null )
			throw new RuntimeException( "This XML does not have the 'Angle' attribute for their ViewSetup. Cannot continue." );

		return angle;
	}

	protected static Channel getChannel( final BasicViewDescription< ? > vd )
	{
		final BasicViewSetup vs = vd.getViewSetup();
		final Channel channel = vs.getAttribute( Channel.class );

		if ( channel == null )
			throw new RuntimeException( "This XML does not have the 'Channel' attribute for their ViewSetup. Cannot continue." );

		return channel;
	}

	protected static Illumination getIllumination( final BasicViewDescription< ? > vd )
	{
		final BasicViewSetup vs = vd.getViewSetup();
		final Illumination illumination = vs.getAttribute( Illumination.class );

		if ( illumination == null )
			throw new RuntimeException( "This XML does not have the 'Illumination' attribute for their ViewSetup. Cannot continue." );

		return illumination;
	}

	@Override
	public String toString()
	{
		return new SlideBook6().getTitle() + ", ImgFactory=" + imgFactory.getClass().getSimpleName();
	}
}
