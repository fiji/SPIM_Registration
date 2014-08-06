package spim.process.fusion.export;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewSetup;

public class XMLTIFFImgTitler implements ImgTitler
{
	final List< TimePoint > timepoints;
	final List< ViewSetup > viewSetups;
	
	public XMLTIFFImgTitler( final List< TimePoint > newTimepoints, final List< ViewSetup > newViewSetups )
	{
		this.timepoints = newTimepoints;
		this.viewSetups = newViewSetups;
	}
	
	@Override
	public String getImageTitle( final TimePoint tp, final ViewSetup vs )
	{
		String filename = "img";

		if ( timepoints.size() > 1 )
			filename += "_TL" + tp.getId();
		
		if ( getAllChannels( viewSetups ).size() > 1 )
			filename += "_Ch" + vs.getChannel().getName();
		
		if ( getAllIlluminations( viewSetups ).size() > 1 )
			filename += "_Ill" + vs.getIllumination().getName();
		
		if ( getAllAngles( viewSetups ).size() > 1 )
			filename += "_Angle" + vs.getAngle().getName();

		return filename;
	}

	public static Set< Angle > getAllAngles( final List< ViewSetup > viewSetups )
	{
		final HashSet< Angle > angles = new HashSet< Angle >();
		
		for ( final ViewSetup v : viewSetups )
			angles.add( v.getAngle() );
		
		return angles;
	}

	public static Set< Channel > getAllChannels( final List< ViewSetup > viewSetups )
	{
		final HashSet< Channel > channels = new HashSet< Channel >();
		
		for ( final ViewSetup v : viewSetups )
			channels.add( v.getChannel() );
		
		return channels;
	}

	public static Set< Illumination > getAllIlluminations( final List< ViewSetup > viewSetups )
	{
		final HashSet< Illumination > illums = new HashSet< Illumination >();
		
		for ( final ViewSetup v : viewSetups )
			illums.add( v.getIllumination() );
		
		return illums;
	}

}
