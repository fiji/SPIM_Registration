package spim.fiji.datasetmanager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;

import loci.formats.FormatException;
import loci.formats.IFormatReader;
import loci.formats.ImageReader;
import loci.formats.Modulo;
import loci.formats.in.ND2Reader;
import loci.formats.in.ZeissCZIReader;
import loci.formats.meta.MetadataRetrieve;
import loci.formats.ome.OMEXMLMetadataImpl;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.VoxelDimensions;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.Dimensions;
import net.imglib2.FinalDimensions;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import ome.units.quantity.Length;
import spim.fiji.datasetmanager.metadatarefinement.CZITileOrAngleRefiner;
import spim.fiji.datasetmanager.metadatarefinement.NikonND2TileOrAngleRefiner;
import spim.fiji.datasetmanager.metadatarefinement.TileOrAngleRefiner;
import spim.fiji.datasetmanager.patterndetector.FilenamePatternDetector;


public class FileListDatasetDefinitionUtil
{
	
	private static final Map<Class<? extends IFormatReader>, TileOrAngleRefiner> tileOrAngleRefiners = new HashMap<>();	
	static {
		tileOrAngleRefiners.put(ZeissCZIReader.class , new CZITileOrAngleRefiner() );
		tileOrAngleRefiners.put( ND2Reader.class, new NikonND2TileOrAngleRefiner() );
	}
	
	
	public static class ChannelInfo
	{
		
		public String name;
		public String fluorophore;
		public Double wavelength;
		@Override
		public int hashCode()
		{
			final int prime = 31;
			int result = 1;
			result = prime * result + ( ( fluorophore == null ) ? 0 : fluorophore.hashCode() );
			result = prime * result + ( ( name == null ) ? 0 : name.hashCode() );
			result = prime * result + ( ( wavelength == null ) ? 0 : wavelength.hashCode() );
			return result;
		}
		
		@Override
		public boolean equals(Object obj)
		{
			if ( this == obj )
				return true;
			if ( obj == null )
				return false;
			if ( getClass() != obj.getClass() )
				return false;
			ChannelInfo other = (ChannelInfo) obj;
			if ( fluorophore == null )
			{
				if ( other.fluorophore != null )
					return false;
			}
			else if ( !fluorophore.equals( other.fluorophore ) )
				return false;
			if ( name == null )
			{
				if ( other.name != null )
					return false;
			}
			else if ( !name.equals( other.name ) )
				return false;
			if ( wavelength == null )
			{
				if ( other.wavelength != null )
					return false;
			}
			else if ( !wavelength.equals( other.wavelength ) )
				return false;
			return true;
		}
		
		public String toString()
		{
			return "Channel name:" + name + " fluorophore:" + fluorophore + " wavelength:" + wavelength;
		}
				
	}
	
	public static class TileInfo
	{
		public Double locationX;
		public Double locationY;
		public Double locationZ;
		
		public String toString()
		{
			return locationX + "," + locationY + "," + locationZ;
		}
		
		@Override
		public int hashCode()
		{
			final int prime = 31;
			int result = 1;
			result = prime * result + ( ( locationX == null ) ? 0 : locationX.hashCode() );
			result = prime * result + ( ( locationY == null ) ? 0 : locationY.hashCode() );
			result = prime * result + ( ( locationZ == null ) ? 0 : locationZ.hashCode() );
			return result;
		}
		@Override
		public boolean equals(Object obj)
		{
			if ( this == obj )
				return true;
			if ( obj == null )
				return false;
			if ( getClass() != obj.getClass() )
				return false;
			TileInfo other = (TileInfo) obj;
			if ( locationX == null )
			{
				if ( other.locationX != null )
					return false;
			}
			else if ( !locationX.equals( other.locationX ) )
				return false;
			if ( locationY == null )
			{
				if ( other.locationY != null )
					return false;
			}
			else if ( !locationY.equals( other.locationY ) )
				return false;
			if ( locationZ == null )
			{
				if ( other.locationZ != null )
					return false;
			}
			else if ( !locationZ.equals( other.locationZ ) )
				return false;
			return true;
		}
	}
	
	public static class AngleInfo
	{
		public Double angle;
		public Integer axis;
		
		@Override
		public int hashCode()
		{
			final int prime = 31;
			int result = 1;
			result = prime * result + ( ( angle == null ) ? 0 : angle.hashCode() );
			result = prime * result + ( ( axis == null ) ? 0 : axis.hashCode() );
			return result;
		}
		@Override
		public boolean equals(Object obj)
		{
			if ( this == obj )
				return true;
			if ( obj == null )
				return false;
			if ( getClass() != obj.getClass() )
				return false;
			AngleInfo other = (AngleInfo) obj;
			if ( angle == null )
			{
				if ( other.angle != null )
					return false;
			}
			else if ( !angle.equals( other.angle ) )
				return false;
			if ( axis == null )
			{
				if ( other.axis != null )
					return false;
			}
			else if ( !axis.equals( other.axis ) )
				return false;
			return true;
		}
	}
	
	public static class TileOrAngleInfo
	{
		public Double locationX;
		public Double locationY;
		public Double locationZ;
		public Double angle;
		public Integer axis;
		public Integer index;
		public Integer channelCount;
		
		public String toString()
		{
			return "TileOrAngleInfo idx:" + index + ", x:" + locationX + ", y:" + locationY + ", z:" + locationZ
					+ ", angle:" + angle + ", axis:" + axis;
		}
	}
	
	
	public static class ChannelOrIlluminationInfo
	{
		public Integer index;
		public Integer modStep;
		public String name;
		public String fluorophore;
		public Double wavelength;
		
		public String toString()
		{
			return "ChannelOrIlluminationInfo idx:" + index + ", modStep:" + modStep + ", name:" + name + ", fluo:" + fluorophore
					+ ", wavelength:" + wavelength;
		}
	}
	
	public enum CheckResult{
		SINGLE,
		MUlTIPLE_NAMED,
		MULTIPLE_INDEXED
	}
	
	public static CheckResult checkMultipleTimepoints(Map<Integer, List<Pair< Integer, Integer >>> tpMap)
	{
		if (tpMap.size() > 1)
			System.out.println( "WARNING: inconsistent timepoint number within file " );
		
		for (Integer tps: tpMap.keySet())
			if (tps > 1)
				return CheckResult.MULTIPLE_INDEXED;
		return CheckResult.SINGLE;
	}
	
	public static < T, S> CheckResult checkMultiplicity (Map<T,List<S>> map)
	{
		if (map.size() > 1)
			return CheckResult.MUlTIPLE_NAMED;
		else if (map.values().iterator().next().size() > 1)			
			return CheckResult.MULTIPLE_INDEXED;
		else
			return CheckResult.SINGLE;
	}
	
	public static List<TileOrAngleInfo> predictTilesAndAngles( IFormatReader r )
	{
		final int nSeries = r.getSeriesCount();
		final MetadataRetrieve mr = (MetadataRetrieve) r.getMetadataStore();
		
		final List<TileOrAngleInfo> result = new ArrayList<>();
		
		for (int i = 0; i < nSeries; i++)
		{
			r.setSeries( i );
			final TileOrAngleInfo infoI = new TileOrAngleInfo();
			infoI.index = i;
			
			// query x position
			Length posX = null;
			try {
				posX = mr.getPlanePositionX( i, 0);
			}
			catch (IndexOutOfBoundsException e)
			{				
			}
			infoI.locationX = posX != null ? posX.value().doubleValue() : null ;
			
			// query y position
			Length posY = null;
			try {
				posY = mr.getPlanePositionY( i, 0);
			}
			catch (IndexOutOfBoundsException e)
			{				
			}
			infoI.locationY = posY != null ? posY.value().doubleValue() : null;
			
			// query z position
			Length posZ = null;
			try {
				posZ = mr.getPlanePositionZ( i, 0);
			}
			catch (IndexOutOfBoundsException e)
			{				
			}
			infoI.locationZ = posZ != null ? posZ.value().doubleValue() : null;
			
			// keep track of "channel" number in series, makes stuff easier elswhere
			infoI.channelCount = r.getSizeC();
			
			result.add( infoI );
		}
		
		return result;
	}
	
	
	public static List<Pair<Integer, List<ChannelOrIlluminationInfo>>> predictTimepointsChannelsAndIllums( IFormatReader r )
	{
		final int nSeries = r.getSeriesCount();
		
		final Modulo cMod = r.getModuloC();			
		final boolean hasModulo = cMod != null && (cMod.start != cMod.end);
		final int cModStep = hasModulo ? (int) cMod.step : r.getSizeC();
		
		final MetadataRetrieve mr = (MetadataRetrieve) r.getMetadataStore();
		
		final List<Pair<Integer, List<ChannelOrIlluminationInfo>>>result = new ArrayList<>();
		
		for (int i = 0; i < nSeries; i++)
		{
			r.setSeries( i );
			final List<ChannelOrIlluminationInfo> channelandIllumInfos = new ArrayList<>();
			for (int c = 0; c < r.getSizeC(); c++)
			{
				final ChannelOrIlluminationInfo infoI = new ChannelOrIlluminationInfo();
				infoI.index = c;
				infoI.modStep = cModStep;
				
				// query channel Name
				infoI.name =  mr.getChannelName( i, c % cModStep );
					
				// query channel fluor
				infoI.fluorophore =  mr.getChannelFluor( i, c % cModStep );
					
				// query channel emission
				Length channelEmissionWavelength = mr.getChannelEmissionWavelength( i, c % cModStep);
				infoI.wavelength = channelEmissionWavelength != null ? channelEmissionWavelength.value().doubleValue() : null ;
				
				channelandIllumInfos.add( infoI );
			}
			
			//channelandIllumInfos.forEach( System.out::println );
			result.add( new ValuePair<>( r.getSizeT(), channelandIllumInfos ));
		}
		
		return result;
	}
	
	public static Pair<Map<TileInfo, List<Pair<Integer, Integer>>>, Map<AngleInfo, List<Pair<Integer, Integer>>>> mapTilesAndAnglesToSeries(List<TileOrAngleInfo> infos)
	{
		final Map<TileInfo, List<Pair<Integer, Integer>>> tileMap = new HashMap<>();
		final Map<AngleInfo, List<Pair<Integer, Integer>>> angleMap = new HashMap<>();
		for (int i = 0; i < infos.size(); i++)
		{
			final TileOrAngleInfo info = infos.get( i );
			
			final TileInfo tI = new TileInfo();
			tI.locationX = info.locationX;
			tI.locationY = info.locationY;
			tI.locationZ = info.locationZ;
			
			if (!tileMap.containsKey( tI ))
				tileMap.put( tI, new ArrayList<>() );
			for (int j = 0; j < info.channelCount; j++)
				tileMap.get( tI ).add( new ValuePair< Integer, Integer >( i, j ) );
			
			final AngleInfo aI = new AngleInfo();
			aI.angle = info.angle;
			aI.axis = info.axis;
			
			if (!angleMap.containsKey( aI ))
				angleMap.put( aI, new ArrayList<>() );
			for (int j = 0; j < info.channelCount; j++)
				angleMap.get( aI ).add( new ValuePair< Integer, Integer >( i, j ) );
		}
		
		return new ValuePair< Map<TileInfo,List<Pair<Integer, Integer>>>, Map<AngleInfo,List<Pair<Integer, Integer>>> >( tileMap, angleMap );
	}
	
	public static Pair<Map<Integer, List<Pair<Integer, Integer>>>, Pair<Map<ChannelInfo, List<Pair<Integer, Integer>>>, Map<Integer, List<Pair<Integer, Integer>>>>> mapTimepointsChannelsAndIlluminations(List<Pair<Integer, List<ChannelOrIlluminationInfo>>> infos)
	{
		// map the number of timepoints to series
		// we know nothing about timepoints a.t.m.
		final Map<Integer, List<Pair<Integer, Integer>>> timepointNumberMap = new HashMap<>();
		
		// map ChannelInfos to series,channel indices
		final Map<ChannelInfo, List<Pair<Integer, Integer>>> channelMap = new HashMap<>();
		
		// map Illumination index to series,channel indices
		final Map<Integer, List<Pair<Integer, Integer>>> illumMap = new HashMap<>();
		
		for (int i = 0 ; i < infos.size(); i++)
		{
			Pair< Integer, List< ChannelOrIlluminationInfo > > seriesInfo = infos.get( i );
			
				
			for (int j = 0 ; j < seriesInfo.getB().size(); j++)
			{
				ChannelOrIlluminationInfo chAndIInfo = seriesInfo.getB().get( j );
				
				final ChannelInfo chI = new ChannelInfo();
				chI.fluorophore = chAndIInfo.fluorophore;
				chI.name = chAndIInfo.name;
				chI.wavelength = chAndIInfo.wavelength;
				
				final Integer illIdx = chAndIInfo.index / chAndIInfo.modStep;
				
				// map channel info
				if (!channelMap.containsKey( chI ))
					channelMap.put( chI, new ArrayList<>() );
				channelMap.get( chI ).add( new ValuePair< Integer, Integer >( i, j ) );
				
				// map illumIdx
				if (!illumMap.containsKey( illIdx ))
					illumMap.put( illIdx, new ArrayList<>() );
				illumMap.get( illIdx ).add( new ValuePair< Integer, Integer >( i, j ) );
				
				// map timepoint number
				final Integer tpCount = seriesInfo.getA();
				if (!timepointNumberMap.containsKey( tpCount ))
					timepointNumberMap.put( tpCount, new ArrayList<>() );
				timepointNumberMap.get( tpCount ).add( new ValuePair< Integer, Integer >(i,j ) );
			}			
		}
		
		return new ValuePair< Map<Integer,List<Pair<Integer, Integer>>>, Pair<Map<ChannelInfo,List<Pair<Integer,Integer>>>,Map<Integer,List<Pair<Integer,Integer>>>> >
					( timepointNumberMap, new ValuePair<Map<ChannelInfo,List<Pair<Integer,Integer>>>,Map<Integer,List<Pair<Integer,Integer>>>> ( channelMap, illumMap ) );
		
	}
	
	protected static ImgFactory< ? extends NativeType< ? > > selectImgFactory( final Map<Pair<File, Pair< Integer, Integer >>, Pair<Dimensions, VoxelDimensions>> dimensionMap )
	{
		long maxNumPixels = 0L;

		for (Pair<Dimensions, VoxelDimensions> p : dimensionMap.values())
		{	
			Dimensions dims = p.getA();
			long n = 1;
			for ( int i = 0; i < dims.numDimensions(); ++i )
				n *= dims.dimension( i );

			maxNumPixels = Math.max( n, maxNumPixels );
			
		}
		
		int smallerLog2 = (int)Math.ceil( Math.log( maxNumPixels ) / Math.log( 2 ) );

		String s = "Maximum number of pixels in any view: n=" + maxNumPixels + 
				" (2^" + (smallerLog2-1) + " < n < 2^" + smallerLog2 + " px), ";

		if ( smallerLog2 <= 31 )
		{
			IOFunctions.println( s + "using ArrayImg." );
			return new ArrayImgFactory< FloatType >();
		}
		else
		{
			IOFunctions.println( s + "using CellImg(256)." );
			return new CellImgFactory< FloatType >( 256 );
		}
	}
	
	public static <T> List<T> listIntersect(List<T> a, List<T> b)
	{
		List<T> result = new ArrayList<>();
		for (T t : a)
			if (b.contains( t ))
				result.add( t );
		return result;
	}
	
	
	public static List<CheckResult> resolveAmbiguity(List<CheckResult> checkResults,
													boolean channelIllumAmbiguous,
													boolean preferChannel,
													boolean angleTileAmbiguous,
													boolean preferTile)
	{
		List<CheckResult> res = new ArrayList<>( checkResults );
		if (channelIllumAmbiguous){
			if (preferChannel)
				res.set( 1, CheckResult.MULTIPLE_INDEXED );
			else
				res.set( 2, CheckResult.MULTIPLE_INDEXED );				
		}
		
		if (angleTileAmbiguous){
			if (preferTile)
				res.set( 3, CheckResult.MULTIPLE_INDEXED );
			else
				res.set( 4, CheckResult.MULTIPLE_INDEXED );
		}
		return res;
	}
	
	public static void expandAccumulatedViewInfos
	(
			final List<CheckResult> multiplicityMap,
			final List<Integer> fileVariableToUse,
			final FilenamePatternDetector patternDetector,
			final Map<Integer, List<Pair<File, Pair< Integer, Integer >>>> accumulateTPMap,
			final Map<FileListDatasetDefinitionUtil.ChannelInfo, List<Pair<File, Pair<Integer, Integer>>>> accumulateChannelMap,
			final Map<Integer, List<Pair<File, Pair<Integer, Integer>>>> accumulateIllumMap,
			final Map<FileListDatasetDefinitionUtil.TileInfo, List<Pair<File, Pair< Integer, Integer >>>> accumulateTileMap,
			final Map<FileListDatasetDefinitionUtil.AngleInfo, List<Pair<File, Pair< Integer, Integer >>>> accumulateAngleMap,
			Map< Integer, List< Pair< File, Pair< Integer, Integer > > > > tpIdxMap,
			Map< Integer, List< Pair< File, Pair< Integer, Integer > > > > channelIdxMap,
			Map< Integer, List< Pair< File, Pair< Integer, Integer > > > > illumIdxMap,
			Map< Integer, List< Pair< File, Pair< Integer, Integer > > > > tileIdxMap,
			Map< Integer, List< Pair< File, Pair< Integer, Integer > > > > angleIdxMap,
			Map< Integer, ChannelInfo > channelDetailMap,
			Map< Integer, TileInfo > tileDetailMap,
			Map< Integer, AngleInfo > angleDetailMap
			
	)
	{
		// DO TIMEPOINTS

		tpIdxMap.clear();
		Boolean singleTPperFile = multiplicityMap.get( 0 ) == CheckResult.SINGLE;

		if ( singleTPperFile && fileVariableToUse.get( 0 ) != null )
		{
			Pair< Map< Integer, Integer >, Map< Integer, List< Pair< File, Pair< Integer, Integer > > > > > expandTPMap = expandMapSingleFromFile(
					accumulateTPMap, patternDetector, fileVariableToUse.get( 0 ) );
			tpIdxMap.putAll( expandTPMap.getB() );
		}
		else if ( singleTPperFile )
		{
			tpIdxMap.put( 0, accumulateTPMap.values().iterator().next() );
		}
		else if ( multiplicityMap.get( 0 ) == CheckResult.MULTIPLE_INDEXED )
		{
			tpIdxMap.putAll( expandTimePointMapIndexed( accumulateTPMap ) );
		}
				
		// DO CHANNELS
		channelIdxMap.clear();
		channelDetailMap.clear();
		Boolean singleChannelperFile = multiplicityMap.get( 1 ) == CheckResult.SINGLE;

		if ( singleChannelperFile && fileVariableToUse.get( 1 ) != null )
		{
			Pair< Map< Integer, ChannelInfo >, Map< Integer, List< Pair< File, Pair< Integer, Integer > > > > > expandChannelMap = expandMapSingleFromFile(
					accumulateChannelMap, patternDetector, fileVariableToUse.get( 1 ) );
			channelIdxMap.putAll( expandChannelMap.getB() );
			channelDetailMap.putAll( expandChannelMap.getA() );
		}
		else if ( singleChannelperFile )
		{

			channelIdxMap.put( 0, accumulateChannelMap.values().iterator().next() );
		}
		else if ( multiplicityMap.get( 1 ) == CheckResult.MULTIPLE_INDEXED )
		{
			channelIdxMap.putAll( expandMapIndexed( accumulateChannelMap, false ) );
		}
		else if ( multiplicityMap.get( 1 ) == CheckResult.MUlTIPLE_NAMED )
		{
			Pair< Map< Integer, ChannelInfo >, Map< Integer, List< Pair< File, Pair< Integer, Integer > > > > > resortMapNamed = resortMapNamed(
					accumulateChannelMap );
			channelDetailMap.putAll( resortMapNamed.getA() );
			channelIdxMap.putAll( resortMapNamed.getB() );
		}
				
		// DO ILLUMINATIONS

		illumIdxMap.clear();
		Boolean singleIllumperFile = multiplicityMap.get( 2 ) == CheckResult.SINGLE;

		if ( singleIllumperFile && fileVariableToUse.get( 2 ) != null )
		{
			Pair< Map< Integer, Integer >, Map< Integer, List< Pair< File, Pair< Integer, Integer > > > > > expandIllumMap = expandMapSingleFromFile(
					accumulateIllumMap, patternDetector, fileVariableToUse.get( 2 ) );
			illumIdxMap.putAll( expandIllumMap.getB() );
		}
		else if ( singleIllumperFile )
		{
			illumIdxMap.put( 0, accumulateIllumMap.values().iterator().next() );
		}
		else if ( multiplicityMap.get( 2 ) == CheckResult.MULTIPLE_INDEXED )
		{
			illumIdxMap.putAll( expandMapIndexed( accumulateIllumMap, false ) );
		}
		else if ( multiplicityMap.get( 2 ) == CheckResult.MUlTIPLE_NAMED )
		{
			Pair< Map< Integer, Integer >, Map< Integer, List< Pair< File, Pair< Integer, Integer > > > > > resortMapNamed = resortMapNamed(
					accumulateIllumMap );
			illumIdxMap.putAll( resortMapNamed.getB() );

		}
				
		// DO TILES

		tileIdxMap.clear();
		tileDetailMap.clear();
		Boolean singleTileperFile = multiplicityMap.get( 3 ) == CheckResult.SINGLE;

		if ( singleTileperFile && fileVariableToUse.get( 3 ) != null )
		{
			Pair< Map< Integer, TileInfo >, Map< Integer, List< Pair< File, Pair< Integer, Integer > > > > > expandTileMap = expandMapSingleFromFile(
					accumulateTileMap, patternDetector, fileVariableToUse.get( 3 ) );
			tileIdxMap.putAll( expandTileMap.getB() );
			tileDetailMap.putAll( expandTileMap.getA() );
		}
		else if ( singleTileperFile )
		{
			tileIdxMap.put( 0, accumulateTileMap.values().iterator().next() );
		}
		else if ( multiplicityMap.get( 3 ) == CheckResult.MULTIPLE_INDEXED )
		{
			tileIdxMap.putAll( expandMapIndexed( accumulateTileMap, true ) );
		}
		else if ( multiplicityMap.get( 3 ) == CheckResult.MUlTIPLE_NAMED )
		{
			Pair< Map< Integer, TileInfo >, Map< Integer, List< Pair< File, Pair< Integer, Integer > > > > > resortMapNamed = resortMapNamed(
					accumulateTileMap );
			tileDetailMap.putAll( resortMapNamed.getA() );
			tileIdxMap.putAll( resortMapNamed.getB() );
		}
				
				
		// DO ANGLES

		angleIdxMap.clear();
		angleDetailMap.clear();
		Boolean singleAngleperFile = multiplicityMap.get( 4 ) == CheckResult.SINGLE;

		if ( singleAngleperFile && fileVariableToUse.get( 4 ) != null )
		{
			Pair< Map< Integer, AngleInfo >, Map< Integer, List< Pair< File, Pair< Integer, Integer > > > > > expandAngleMap = expandMapSingleFromFile(
					accumulateAngleMap, patternDetector, fileVariableToUse.get( 4 ) );
			angleIdxMap.putAll( expandAngleMap.getB() );
			angleDetailMap.putAll( expandAngleMap.getA() );
		}
		else if ( singleAngleperFile )
		{
			angleIdxMap.put( 0, accumulateAngleMap.values().iterator().next() );
		}
		else if ( multiplicityMap.get( 4 ) == CheckResult.MULTIPLE_INDEXED )
		{
			angleIdxMap.putAll( expandMapIndexed( accumulateAngleMap, true ) );
		}
		else if ( multiplicityMap.get( 4 ) == CheckResult.MUlTIPLE_NAMED )
		{
			Pair< Map< Integer, AngleInfo >, Map< Integer, List< Pair< File, Pair< Integer, Integer > > > > > resortMapNamed = resortMapNamed(
					accumulateAngleMap );
			angleDetailMap.putAll( resortMapNamed.getA() );
			angleIdxMap.putAll( resortMapNamed.getB() );
		}
	}
	
	public static <T> Pair<Map<Integer, T>, Map<Integer, List<Pair<File, Pair< Integer, Integer >>>>> expandMapSingleFromFile(Map<T, List<Pair<File, Pair< Integer, Integer >>>> map, FilenamePatternDetector det, int patternIdx)
	{
		Map<Integer, List<Pair<File, Pair< Integer, Integer >>>> res = new HashMap<>();
		Map<Integer, T> res2 = new HashMap<>();
		SortedMap< Pair< File, Pair< Integer, Integer > >, T > invertedMap = invertMapSortValue( map );
		for (Pair< File, Pair< Integer, Integer > > fileInfo : invertedMap.keySet())
		{
			T attribute = invertedMap.get( fileInfo );
			//System.out.println( fileInfo.getA().getAbsolutePath() );
			int id = 0;
			Matcher m = det.getPatternAsRegex().matcher( fileInfo.getA().getAbsolutePath() );
			if (m.matches())
				id = Integer.parseInt( m.group( patternIdx + 1 ));
			else
				System.out.println( "WARNING: something went wrong while matching filenames" );
			res2.put( id, attribute );
			
			if (!res.containsKey( id ))
				res.put( id, new ArrayList<>() );
			res.get( id ).add( fileInfo );
		}
		return new ValuePair< Map<Integer,T>, Map<Integer,List<Pair<File,Pair<Integer,Integer>>>> >( res2, res );
			
	}
	
	public static <T> Pair<Map<Integer, T>, Map<Integer, List<Pair<File, Pair< Integer, Integer >>>>> resortMapNamed(Map<T, List<Pair<File, Pair< Integer, Integer >>>> map)
	{
		
		Map<Integer, List<Pair<File, Pair< Integer, Integer >>>> res = new HashMap<>();
		Map<Integer, T> res2 = new HashMap<>();
		SortedMap< Pair< File, Pair< Integer, Integer > >, T > invertedMap = invertMapSortValue( map );
		int maxId = 0;
		for (Pair< File, Pair< Integer, Integer > > fileInfo : invertedMap.keySet())
		{
			int id= 0;
			T attribute = invertedMap.get( fileInfo );
			if (!res2.values().contains( attribute ))
			{
				res2.put( maxId, attribute );
				id = maxId;
				maxId++;
			}
			else
			{
				for (Integer i : res2.keySet())
					if (res2.get( i ).equals( attribute ))
						id = i;
			}
			
			if (!res.containsKey( id ))
				res.put( id, new ArrayList<>() );
			res.get( id ).add( fileInfo );
		}
		return new ValuePair< Map<Integer,T>, Map<Integer,List<Pair<File,Pair<Integer,Integer>>>> >( res2, res );
	}
	
	
	public static <T> Map<Integer, List<Pair<File, Pair< Integer, Integer >>>> expandMapIndexed(Map<T, List<Pair<File, Pair< Integer, Integer >>>> map, boolean useSeries)
	{
		Map<Integer, List<Pair<File, Pair< Integer, Integer >>>> res = new HashMap<>();
		SortedMap< Pair< File, Pair< Integer, Integer > >, T > invertedMap = invertMapSortValue( map );
		for (Pair< File, Pair< Integer, Integer > > fileInfo : invertedMap.keySet())
		{
			int id = useSeries ? fileInfo.getB().getA() : fileInfo.getB().getB();
			if (!res.containsKey( id ))
				res.put( id, new ArrayList<>() );
			res.get( id ).add( fileInfo );
		}
		return res;
	}
	
	public static Map<Integer, List<Pair<File, Pair< Integer, Integer >>>> expandTimePointMapIndexed(Map<Integer, List<Pair<File, Pair< Integer, Integer >>>> map)
	{
		Map<Integer, List<Pair<File, Pair< Integer, Integer >>>> res = new HashMap<>();
		SortedMap< Pair< File, Pair< Integer, Integer > >, Integer > invertedMap = invertMapSortValue( map );
		for (Pair< File, Pair< Integer, Integer > > fileInfo : invertedMap.keySet())
		{
			Integer numTP = invertedMap.get( fileInfo );
			for (int i = 0; i < numTP; i++)
			{
				if (!res.containsKey( i ))
					res.put( i, new ArrayList<>() );
				res.get( i ).add( fileInfo );
			}
		}
		return res;
	}
	
	
	public static <T> SortedMap<Pair<File, Pair< Integer, Integer >>, T> invertMapSortValue(Map<T, List<Pair<File, Pair< Integer, Integer >>>> map )
	{
			 
		SortedMap<Pair<File, Pair< Integer, Integer >>, T> res = new TreeMap<Pair<File, Pair< Integer, Integer >>, T>( new Comparator< Pair<File, Pair< Integer, Integer >> >()
		{

			@Override
			public int compare(Pair< File, Pair< Integer, Integer > > o1, Pair< File, Pair< Integer, Integer > > o2)
			{
				int filecompare = o1.getA().getAbsolutePath().compareTo( o2.getA().getAbsolutePath() ) ;
				if (filecompare != 0)
					return filecompare;
				
				int seriescompare = o1.getB().getA().compareTo( o2.getB().getA() ); 
				if (seriescompare != 0)
					return seriescompare;
				
				return o1.getB().getB().compareTo( o2.getB().getB() );
			}
		} );
		
		for (T key : map.keySet())
		{
			for (Pair<File, Pair< Integer, Integer >> vI : map.get( key ))
			{
				//System.out.println( vI.getB().getA() + "" + vI.getB().getB() );
				//System.out.println( key );
				res.put( vI, key );
			}
		}
		//System.out.println( res.size() );
		return res;		 
	}
	
	public static void detectDimensionsInFile(File file, Map<Pair<File, Pair< Integer, Integer >>, Pair<Dimensions, VoxelDimensions>> dimensionMaps)
	{
		System.out.println( file );
		IFormatReader reader = new ImageReader();
		reader.setMetadataStore( new OMEXMLMetadataImpl());
		try
		{
			reader.setId( file.getAbsolutePath() );
		
		
		for (int i = 0 ; i < reader.getSeriesCount(); i++)
		{
			reader.setSeries( i );
			MetadataRetrieve meta = (MetadataRetrieve)reader.getMetadataStore();
			
			double sizeX = 1;
			double sizeY = 1;
			double sizeZ = 1;
			
			Length pszX = null;
			try {
				pszX = meta.getPixelsPhysicalSizeX( i );
			}
			catch (IndexOutOfBoundsException e)
			{				
			}
			sizeX = pszX != null ? pszX.value().doubleValue() : null ;
			
			Length pszY = null;
			try {
				pszY = meta.getPixelsPhysicalSizeY( i );
			}
			catch (IndexOutOfBoundsException e)
			{				
			}
			sizeY = pszY != null ? pszY.value().doubleValue() : null ;
			
			Length pszZ = null;
			try {
				pszZ = meta.getPixelsPhysicalSizeZ( i );
			}
			catch (IndexOutOfBoundsException e)
			{				
			}
			sizeZ = pszZ != null ? pszZ.value().doubleValue() : null ;
			
			int dimX = reader.getSizeX();
			int dimY = reader.getSizeY();
			int dimZ = reader.getSizeZ();
			
			
			FinalVoxelDimensions finalVoxelDimensions = new FinalVoxelDimensions( "units", sizeX, sizeY, sizeZ );
			FinalDimensions finalDimensions = new FinalDimensions( dimX, dimY, dimZ );
			
			for (int j = 0; j < reader.getSizeC(); j++)
			{			
				Pair<File, Pair< Integer, Integer >> key = new ValuePair< File, Pair<Integer,Integer> >( file, new ValuePair< Integer, Integer >( i, j ) );
				dimensionMaps.put( key, new ValuePair< Dimensions, VoxelDimensions >( finalDimensions, finalVoxelDimensions ) );
			}
			
		}
			
		reader.close();
		}

		catch ( FormatException | IOException e1 )
		{
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}
	
	public static void detectViewsInFiles(List<File> files,
										 List<CheckResult> multiplicityMap,
										 Map<Integer, List<Pair<File, Pair< Integer, Integer >>>> accumulateTPMap,
										 Map<ChannelInfo, List<Pair<File, Pair<Integer, Integer>>>> accumulateChannelMap,
										 Map<Integer, List<Pair<File, Pair<Integer, Integer>>>> accumulateIllumMap,
										 Map<TileInfo, List<Pair<File, Pair< Integer, Integer >>>> accumulateTileMap,
										 Map<AngleInfo, List<Pair<File, Pair< Integer, Integer >>>> accumulateAngleMap,
										 Boolean ambiguousAngleTile,
										 Boolean ambiguousIllumChannel,
										 Map<Pair<File, Pair< Integer, Integer >>, Pair<Dimensions, VoxelDimensions>> dimensionMaps)
	{
		Map<File, List<CheckResult>> multiplicityMapInner = new HashMap<>();
		List<String> usedFiles = new ArrayList<>();
		
		
		for (File file : files)
			if (!usedFiles.contains( file.getAbsolutePath() ))
			{
				detectViewsInFile( 	file,
									multiplicityMapInner,
									accumulateTPMap,
									accumulateChannelMap,
									accumulateIllumMap,
									accumulateTileMap, 
									accumulateAngleMap, 
									ambiguousAngleTile,
									ambiguousIllumChannel,
									usedFiles);
				
				detectDimensionsInFile (file, dimensionMaps);
			}
		
		
		for (List<CheckResult> cr : multiplicityMapInner.values())
		{
			for (int i = 0; i < multiplicityMap.size(); i++)
			{
				if (multiplicityMap.get( i ) == CheckResult.SINGLE && cr.get( i ) == CheckResult.MULTIPLE_INDEXED)
					multiplicityMap.set( i, CheckResult.MULTIPLE_INDEXED );
				else if (multiplicityMap.get( i ) == CheckResult.SINGLE && cr.get( i ) == CheckResult.MUlTIPLE_NAMED)
					multiplicityMap.set( i, CheckResult.MUlTIPLE_NAMED );
				// TODO: Error here if we have mixed indexed and named
			}
		}
		
	}
	
	
	public static void detectViewsInFile(final File file,
										 Map<File, List<CheckResult>> multiplicityMap,
										 Map<Integer, List<Pair<File, Pair< Integer, Integer >>>> accumulateTPMap,
										 Map<ChannelInfo, List<Pair<File, Pair<Integer, Integer>>>> accumulateChannelMap,
										 Map<Integer, List<Pair<File, Pair<Integer, Integer>>>> accumulateIllumMap,
										 Map<TileInfo, List<Pair<File, Pair< Integer, Integer >>>> accumulateTileMap,
										 Map<AngleInfo, List<Pair<File, Pair< Integer, Integer >>>> accumulateAngleMap,
										 Boolean ambiguousAngleTile,
										 Boolean ambiguousIllumChannel,
										 List<String> usedFiles)
	{
		ImageReader reader = new ImageReader();
		reader.setMetadataStore( new OMEXMLMetadataImpl());
		System.out.println( "Investigating file: " + file.getAbsolutePath() );
		
		try
		{
			reader.setId( file.getAbsolutePath() );
			
			usedFiles.addAll( Arrays.asList( reader.getUsedFiles() ));
			
			// predict tiles and angles, refine info with format specific refiner
			List< TileOrAngleInfo > predictTilesAndAngles = predictTilesAndAngles( reader);			
			TileOrAngleRefiner refiner = tileOrAngleRefiners.get( ((ImageReader)reader).getReader().getClass() );
			if (refiner != null)
				refiner.refineTileOrAngleInfo( reader, predictTilesAndAngles );
			
			// map to tileMap and angleMap
			Pair< Map< TileInfo, List< Pair< Integer, Integer > > >, Map< AngleInfo, List< Pair< Integer, Integer > > > > mapTilesAngles = mapTilesAndAnglesToSeries( predictTilesAndAngles );
			Map< TileInfo, List< Pair< Integer, Integer > > > tileMap = mapTilesAngles.getA();
			Map< AngleInfo, List< Pair< Integer, Integer > > > angleMap = mapTilesAngles.getB();
			
			// predict and map timepoints, channels, illuminations
			List< Pair< Integer, List< ChannelOrIlluminationInfo > > > predictTPChannelsIllum = predictTimepointsChannelsAndIllums( reader );
			Pair< Map< Integer, List< Pair< Integer, Integer > > >, Pair< Map< ChannelInfo, List< Pair< Integer, Integer > > >, Map< Integer, List< Pair< Integer, Integer > > > > > mapTimepointsChannelsIlluminations = mapTimepointsChannelsAndIlluminations(predictTPChannelsIllum);
			Map< Integer, List< Pair< Integer, Integer > > > timepointMap = mapTimepointsChannelsIlluminations.getA();
			Map< ChannelInfo, List< Pair< Integer, Integer > > > channelMap = mapTimepointsChannelsIlluminations.getB().getA();
			Map< Integer, List< Pair< Integer, Integer > > > illumMap = mapTimepointsChannelsIlluminations.getB().getB();
			
			// check multiplicity of maps
			CheckResult timepointMultiplicity = checkMultipleTimepoints( timepointMap );
			CheckResult channelMultiplicity = checkMultiplicity( channelMap );
			CheckResult illuminationMultiplicity = checkMultiplicity( illumMap );
			CheckResult angleMultiplicity = checkMultiplicity( angleMap );
			CheckResult tileMultiplicity = checkMultiplicity( tileMap );
			
			boolean channelIllumAmbiguous = false;
			if (channelMultiplicity == CheckResult.MULTIPLE_INDEXED && illuminationMultiplicity == CheckResult.MUlTIPLE_NAMED)
				channelMultiplicity = CheckResult.SINGLE;
			else if (channelMultiplicity == CheckResult.MUlTIPLE_NAMED && illuminationMultiplicity == CheckResult.MULTIPLE_INDEXED)
				illuminationMultiplicity = CheckResult.SINGLE;
			else if (channelMultiplicity == CheckResult.MULTIPLE_INDEXED && illuminationMultiplicity == CheckResult.MULTIPLE_INDEXED)
			{
				channelMultiplicity = CheckResult.SINGLE;
				illuminationMultiplicity = CheckResult.SINGLE;
				channelIllumAmbiguous = true;
			}
			
			boolean angleTileAmbiguous = false;
			if (tileMultiplicity == CheckResult.MULTIPLE_INDEXED && angleMultiplicity == CheckResult.MUlTIPLE_NAMED)
				tileMultiplicity = CheckResult.SINGLE;
			else if (tileMultiplicity == CheckResult.MUlTIPLE_NAMED && angleMultiplicity == CheckResult.MULTIPLE_INDEXED)
				angleMultiplicity = CheckResult.SINGLE;
			else if (tileMultiplicity == CheckResult.MULTIPLE_INDEXED && angleMultiplicity == CheckResult.MULTIPLE_INDEXED)
			{
				tileMultiplicity = CheckResult.SINGLE;
				angleMultiplicity = CheckResult.SINGLE;
				angleTileAmbiguous = true;				
			}
			
			
			// TODO: handle tiles cleanly
			/*
			else if (tileMultiplicity == CheckResult.MUlTIPLE_NAMED && angleMultiplicity == CheckResult.MUlTIPLE_NAMED && tileMap.size() == angleMap.size())
				tileMultiplicity = CheckResult.SINGLE;
			*/
			
			List<CheckResult> checkResults = Arrays.asList( new CheckResult[] 
					{timepointMultiplicity, channelMultiplicity, illuminationMultiplicity, tileMultiplicity, angleMultiplicity} );
			
			multiplicityMap.put( file, checkResults );
			
			for (Integer tp : timepointMap.keySet())
			{
				if (!accumulateTPMap.containsKey( tp ))
					accumulateTPMap.put( tp, new ArrayList<>() );
				timepointMap.get( tp ).forEach( series -> accumulateTPMap.get( tp ).add( new ValuePair< File, Pair< Integer, Integer > >( file, series ) ) );
			}
			
			for (ChannelInfo ch : channelMap.keySet())
			{
				//System.out.println( "DEBUG: Processing channel " + ch );
				//System.out.println( "DEBUG: in file " + file.getAbsolutePath() );
				if (!accumulateChannelMap.containsKey( ch ))
				{
					//System.out.println( "DEBUG: did not find channel, adding new" );
					accumulateChannelMap.put( ch, new ArrayList<>() );
				}
				channelMap.get( ch ).forEach( seriesAndIdx -> accumulateChannelMap.get( ch ).add( new ValuePair< File, Pair<Integer,Integer> >( file, seriesAndIdx) ) );
			}
			
			for (Integer il : illumMap.keySet())
			{
				if (!accumulateIllumMap.containsKey( il ))
					accumulateIllumMap.put( il, new ArrayList<>() );
				illumMap.get( il ).forEach( seriesAndIdx -> accumulateIllumMap.get( il ).add( new ValuePair< File, Pair<Integer,Integer> >( file, seriesAndIdx) ));
			}
			
			for (TileInfo t : tileMap.keySet())
			{
				if(!accumulateTileMap.containsKey( t ))
					accumulateTileMap.put( t, new ArrayList<>() );
				tileMap.get( t ).forEach( seriesAndIdx -> accumulateTileMap.get( t ).add( new ValuePair< File, Pair<Integer,Integer> >( file, seriesAndIdx) ) );
			}
			
			for (AngleInfo a : angleMap.keySet())
			{
				if(!accumulateAngleMap.containsKey( a ))
					accumulateAngleMap.put( a, new ArrayList<>() );
				angleMap.get( a ).forEach( seriesAndIdx -> accumulateAngleMap.get( a ).add( new ValuePair< File, Pair<Integer,Integer> >( file, seriesAndIdx) ) );
			}
			
			if (!ambiguousAngleTile && angleTileAmbiguous)
				ambiguousAngleTile = true;
			
			if(!ambiguousIllumChannel && channelIllumAmbiguous)
				ambiguousIllumChannel = true;
			
			reader.close();

		}
		catch ( FormatException e )
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch ( IOException e )
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		
	}

}
