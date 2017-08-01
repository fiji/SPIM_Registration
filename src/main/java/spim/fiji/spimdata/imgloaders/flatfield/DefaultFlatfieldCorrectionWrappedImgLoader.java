package spim.fiji.spimdata.imgloaders.flatfield;

import java.io.File;

import bdv.util.BdvFunctions;
import ij.ImageJ;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.XmlIoSpimData;
import mpicbg.spim.data.generic.sequence.ImgLoaderHint;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.SetupImgLoader;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Dimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import spim.fiji.plugin.queryXML.GenericLoadParseQueryXML;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.XmlIoSpimData2;
import spim.fiji.spimdata.imgloaders.XmlIoFileListImgLoaderLOCI;
import spim.process.fusion.FusionTools;

public class DefaultFlatfieldCorrectionWrappedImgLoader extends LazyLoadingFlatFieldCorrectionMap< ImgLoader > implements ImgLoader
{
	private ImgLoader wrappedImgLoader;
	private boolean active;
	
	public DefaultFlatfieldCorrectionWrappedImgLoader(ImgLoader wrappedImgLoader)
	{
		super();
		this.wrappedImgLoader = wrappedImgLoader;
		this.active = true;
	}
	
	@Override
	public ImgLoader getWrappedImgLoder()
	{
		return wrappedImgLoader;
	}

	@Override
	public void setActive(boolean active)
	{
		this.active = active;
	}
	
	@Override
	public boolean isActive()
	{
		return active;
	}
	
	@Override
	public SetupImgLoader< ? > getSetupImgLoader(int setupId)
	{
		return new DefaultFlatfieldCorrectionWrappedSetupImgLoader<>(setupId);
	}
	
	class DefaultFlatfieldCorrectionWrappedSetupImgLoader <T extends RealType< T > & NativeType< T >> implements SetupImgLoader< T >
	{
		private final int setupId;
		
		DefaultFlatfieldCorrectionWrappedSetupImgLoader(int setupId)
		{
			this.setupId = setupId;
		}

		@Override
		public RandomAccessibleInterval< T > getImage(int timepointId, ImgLoaderHint... hints)
		{
			// TODO: cache?
			
			final RandomAccessibleInterval< T > rai = FlatFieldCorrectedRandomAccessibleIntervals.create(  
					(RandomAccessibleInterval< T >) wrappedImgLoader.getSetupImgLoader( setupId ).getImage( timepointId, hints ),
					getBrightImg( new ViewId(timepointId, setupId) ),
					getDarkImg( new ViewId( timepointId, setupId ) ) );
			
			return FusionTools.cacheRandomAccessibleInterval( rai, Long.MAX_VALUE, Views.iterable( rai ).firstElement().createVariable());
		}

		

		@Override
		public RandomAccessibleInterval< FloatType > getFloatImage(int timepointId, boolean normalize,
				ImgLoaderHint... hints)
		{
			final RandomAccessibleInterval< FloatType > rai = FlatFieldCorrectedRandomAccessibleIntervals.create(  
					(RandomAccessibleInterval< T >) wrappedImgLoader.getSetupImgLoader( setupId ).getImage( timepointId, hints ),
					getBrightImg( new ViewId(timepointId, setupId) ),
					getDarkImg( new ViewId( timepointId, setupId ) ),
					new FloatType());

			// TODO: respect normalize
			// TODO: good cell dimensions (planes?)
			return FusionTools.cacheRandomAccessibleInterval( rai, Long.MAX_VALUE, new FloatType(), 10);
		}

		@Override
		public T getImageType() { return (T) wrappedImgLoader.getSetupImgLoader( setupId ).getImageType(); }

		@Override
		public Dimensions getImageSize(int timepointId) { return wrappedImgLoader.getSetupImgLoader( setupId ).getImageSize( timepointId ); }

		@Override
		public VoxelDimensions getVoxelSize(int timepointId) { return wrappedImgLoader.getSetupImgLoader( setupId ).getVoxelSize( timepointId ); }
	}
	
	public static void main(String[] args)
	{
		GenericLoadParseQueryXML< SpimData2, SequenceDescription, ViewSetup, ViewDescription, ImgLoader, XmlIoSpimData2 > lpq = new GenericLoadParseQueryXML<>( new XmlIoSpimData2("") );
		lpq.queryXML();
		SpimData2 data = lpq.getData();
		
		ImgLoader il = data.getSequenceDescription().getImgLoader();
		DefaultFlatfieldCorrectionWrappedImgLoader ffcil = new DefaultFlatfieldCorrectionWrappedImgLoader(il);
		ffcil.setDarkImage( new ViewId(0,0), new File("/Users/david/desktop/ff.tif") );
		
		new ImageJ();
		BdvFunctions.show( ffcil.getSetupImgLoader( 0 ).getFloatImage( 0, false ), "BDV");
		
		
	}

}
