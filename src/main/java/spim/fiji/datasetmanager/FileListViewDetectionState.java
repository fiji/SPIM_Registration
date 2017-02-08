package spim.fiji.datasetmanager;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.security.auth.Destroyable;

import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.Tile;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Dimensions;
import net.imglib2.util.Pair;
import spim.fiji.datasetmanager.FileListDatasetDefinitionUtil.AngleInfo;
import spim.fiji.datasetmanager.FileListDatasetDefinitionUtil.ChannelInfo;
import spim.fiji.datasetmanager.FileListDatasetDefinitionUtil.CheckResult;
import spim.fiji.datasetmanager.FileListDatasetDefinitionUtil.TileInfo;

public class FileListViewDetectionState
{
	Boolean ambiguousAngleTile;
	Boolean ambiguousIllumChannel;
	
	Map<Class<? extends Entity>, FileListDatasetDefinitionUtil.CheckResult> multiplicityMap;	
	Map<Class<? extends Entity>, Map<Object, List<Pair<File, Pair< Integer, Integer >>>>> accumulativeMap;	
	Map<Class<? extends Entity>, Map< Integer, List< Pair< File, Pair< Integer, Integer > > > >> idMap;	
	Map<Class<? extends Entity>, Map< Integer, Object>> detailMap;
	
	
	Map<Pair<File, Pair< Integer, Integer >>, Pair<Dimensions, VoxelDimensions>> dimensionMap;
	
	
	public FileListViewDetectionState()
	{
		multiplicityMap = new HashMap<>();		
		accumulativeMap = new HashMap<>();		
		idMap = new HashMap<>();		
		detailMap = new HashMap<>();
		
		for (Class<? extends Entity> cl : new Class[] {Angle.class, TimePoint.class, Illumination.class, Tile.class, Channel.class})
		{
			multiplicityMap.put( cl, CheckResult.SINGLE  );
			accumulativeMap.put( cl, new HashMap<>() );
			idMap.put( cl, new HashMap<>() );
			detailMap.put( cl, new HashMap<>() );
		}
		
		ambiguousAngleTile = false;
		ambiguousIllumChannel = false;
		
		dimensionMap = new HashMap<>();
		
	}

	public Map<Class<? extends Entity>, FileListDatasetDefinitionUtil.CheckResult> getMultiplicityMap()
	{
		return multiplicityMap;
	}

	public Boolean getAmbiguousAngleTile()
	{
		return ambiguousAngleTile;
	}

	public Boolean getAmbiguousIllumChannel()
	{
		return ambiguousIllumChannel;
	}
	
	public Map<Object, List< Pair< File, Pair< Integer, Integer > > >> getAccumulateMap(Class<? extends Entity> cl)
	{
		return accumulativeMap.get( cl );
	}
	

//	public Map< Integer, List< Pair< File, Pair< Integer, Integer > > > > getAccumulateTPMap()
//	{
//		return accumulateTPMap;
//	}
//
//	public Map< FileListDatasetDefinitionUtil.ChannelInfo, List< Pair< File, Pair< Integer, Integer > > > > getAccumulateChannelMap()
//	{
//		return accumulateChannelMap;
//	}
//
//	public Map< Integer, List< Pair< File, Pair< Integer, Integer > > > > getAccumulateIllumMap()
//	{
//		return accumulateIllumMap;
//	}
//
//	public Map< FileListDatasetDefinitionUtil.TileInfo, List< Pair< File, Pair< Integer, Integer > > > > getAccumulateTileMap()
//	{
//		return accumulateTileMap;
//	}
//
//	public Map< FileListDatasetDefinitionUtil.AngleInfo, List< Pair< File, Pair< Integer, Integer > > > > getAccumulateAngleMap()
//	{
//		return accumulateAngleMap;
//	}

	public Map< Pair< File, Pair< Integer, Integer > >, Pair< Dimensions, VoxelDimensions > > getDimensionMap()
	{
		return dimensionMap;
	}

	public void setAmbiguousAngleTile(Boolean ambiguousAngleTile)
	{
		this.ambiguousAngleTile = ambiguousAngleTile;
	}

	public void setAmbiguousIllumChannel(Boolean ambiguousIllumChannel)
	{
		this.ambiguousIllumChannel = ambiguousIllumChannel;
	}

	public Map< Class< ? extends Entity >, Map< Integer, List< Pair< File, Pair< Integer, Integer > > > > > getIdMap()
	{
		return idMap;
	}

	

	public Map< Class< ? extends Entity >, Map< Integer, Object > > getDetailMap()
	{
		return detailMap;
	}

	
	

}
