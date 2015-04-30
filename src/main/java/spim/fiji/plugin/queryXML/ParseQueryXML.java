package spim.fiji.plugin.queryXML;

import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewSetup;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.XmlIoSpimData2;

/**
 * Created by moon on 4/28/15.
 * For the cluster process, it uses clusterExtention.
 */
public class ParseQueryXML extends GenericLoadParseQueryXML< SpimData2, SequenceDescription, ViewSetup, ViewDescription, ImgLoader< ? >, XmlIoSpimData2 >
{
	public ParseQueryXML()
	{
		super( new XmlIoSpimData2( "" ) );
	}

	public boolean queryXML( )
	{
		String xmlFilename = defaultXMLfilename;
		// try to parse the file anyways
		tryParsing( xmlFilename, true );

		if ( !queryDetails() )
			return false;

		//this.clusterExt = "job_" + createUniqueName();

		return true;
	}
}