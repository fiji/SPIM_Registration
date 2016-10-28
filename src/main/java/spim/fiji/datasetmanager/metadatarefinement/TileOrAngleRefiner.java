package spim.fiji.datasetmanager.metadatarefinement;

import java.util.List;

import loci.formats.IFormatReader;
import spim.fiji.datasetmanager.FileListDatasetDefinitionUtil;
import spim.fiji.datasetmanager.FileListDatasetDefinitionUtil.TileOrAngleInfo;

public interface TileOrAngleRefiner
{
	public void refineTileOrAngleInfo( IFormatReader r, List<FileListDatasetDefinitionUtil.TileOrAngleInfo> infos);
}