package spim.headless.Resave;

import bdv.export.ProgressWriter;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.sequence.ViewId;
import spim.fiji.plugin.resave.Generic_Resave_HDF5;
import spim.fiji.plugin.resave.ProgressWriterIJ;
import spim.fiji.plugin.resave.Resave_HDF5;
import spim.fiji.spimdata.SpimData2;
import spim.process.fusion.export.ExportSpimData2HDF5;
/**
 * Created by schmied on 02/07/15.
 */
public class ResaveHdf5 {

    public static void save( final String xmlfile, final ResaveHdf5Parameter params )
    {

        Generic_Resave_HDF5.Parameters newParameters = new Generic_Resave_HDF5.Parameters(
                params.setMipmapManual,
                params.resolutions,
                params.subdivisions,
                params.seqFile,
                params.hdf5File,
                params.deflate,
                params.split,
                params.timepointsPerPartition,
                params.setupsPerPartition,
                params.onlyRunSingleJob,
                params.jobId,
                params.convertChoice,
                params.min,
                params.max
                );

        final ProgressWriter progressWriter = new ProgressWriterIJ();
        progressWriter.out().println( "starting export..." );

        final HeadlessParseQueryXML xml = new HeadlessParseQueryXML();
        final String xmlFileName = params.getXmlFilename();
        if ( !xml.loadXML( xmlFileName, params.isUseCluster() ) ) return;

        final SpimData2 data = xml.getData();
        final List<ViewId> viewIds = SpimData2.getAllViewIdsSorted( data, xml.getViewSetupsToProcess(), xml.getTimePointsToProcess() );

        // write hdf5
        Generic_Resave_HDF5.writeHDF5(Resave_HDF5.reduceSpimData2(data, viewIds), params, progressWriter);

        // write xml sequence description
        try
        {
            ExportSpimData2HDF5.setXMLData(spimData2, io, newParameters, progressWriter);
        }
        catch ( SpimDataException e )
        {
            throw new RuntimeException( e );
        }
    }

}
