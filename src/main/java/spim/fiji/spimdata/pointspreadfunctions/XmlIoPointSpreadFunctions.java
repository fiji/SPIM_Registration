package spim.fiji.spimdata.pointspreadfunctions;

import static spim.fiji.spimdata.pointspreadfunctions.XmlKeysPointSpreadFunctions.PSFS_TAG;
import static spim.fiji.spimdata.pointspreadfunctions.XmlKeysPointSpreadFunctions.PSF_FILE_TAG;
import static spim.fiji.spimdata.pointspreadfunctions.XmlKeysPointSpreadFunctions.PSF_SETUP_ATTRIBUTE_NAME;
import static spim.fiji.spimdata.pointspreadfunctions.XmlKeysPointSpreadFunctions.PSF_TAG;
import static spim.fiji.spimdata.pointspreadfunctions.XmlKeysPointSpreadFunctions.PSF_TIMEPOINT_ATTRIBUTE_NAME;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;

import org.jdom2.Element;

import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.XmlHelpers;
import mpicbg.spim.data.generic.base.XmlIoSingleton;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;

public class XmlIoPointSpreadFunctions extends XmlIoSingleton< PointSpreadFunctions >
{
	public XmlIoPointSpreadFunctions()
	{
		super( PSFS_TAG, PointSpreadFunctions.class );
		handledTags.add( PSF_TAG );
	}

	public Element toXml( final PointSpreadFunctions pointSpreadFunctions )
	{
		final Element elem = super.toXml();

		// sort all entries by timepoint and viewsetupid so that it is possible to edit XML by hand
		final ArrayList< ViewId > views = new ArrayList<>();
		views.addAll( pointSpreadFunctions.getPointSpreadFunctions().keySet() );
		Collections.sort( views );

		for ( final ViewId v : views )
			elem.addContent( psfToXml( pointSpreadFunctions.getPointSpreadFunctions().get( v ), v ) );

		return elem;
	}

	public PointSpreadFunctions fromXml( final Element allPSFs, final File basePath ) throws SpimDataException
	{
		final PointSpreadFunctions pointSpreadFunctions = super.fromXml( allPSFs );

		for ( final Element psfElement : allPSFs.getChildren( PSF_TAG ) )
		{
			final String file = psfElement.getAttributeValue( PSF_FILE_TAG );
			final int tpId = XmlHelpers.getInt( psfElement, PSF_TIMEPOINT_ATTRIBUTE_NAME );
			final int vsId = XmlHelpers.getInt( psfElement, PSF_SETUP_ATTRIBUTE_NAME );

			pointSpreadFunctions.addPSF( new ViewId( tpId, vsId ), new PointSpreadFunction( basePath, file ) );
		}

		return pointSpreadFunctions;
	}

	protected Element psfToXml( final PointSpreadFunction psf, final ViewId viewId )
	{
		final Element elem = new Element( PSF_TAG );

		elem.setAttribute( PSF_FILE_TAG, psf.getFile() );
		elem.addContent( XmlHelpers.intElement( PSF_TIMEPOINT_ATTRIBUTE_NAME, viewId.getTimePointId() ) );
		elem.addContent( XmlHelpers.intElement( PSF_SETUP_ATTRIBUTE_NAME, viewId.getViewSetupId() ) );

		if ( psf.isModified() )
			if ( !psf.save() )
				IOFunctions.println( "ERROR: Could not save PSF '" + psf.getFile() + "'" );

		return elem;
	}
}
