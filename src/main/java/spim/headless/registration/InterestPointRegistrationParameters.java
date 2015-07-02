package spim.headless.registration;

import java.util.List;
import java.util.Map;

import mpicbg.spim.data.sequence.ViewId;
import spim.fiji.ImgLib2Temp.Pair;
import spim.fiji.spimdata.interestpoints.InterestPointList;

public class InterestPointRegistrationParameters extends RegistrationParameters
{
	Map< ViewId, InterestPointList > interestpoints;

	/** This results in a list of corresponding interest points **/
	List< Pair< ViewId, ViewId > > pairs;

	
}
