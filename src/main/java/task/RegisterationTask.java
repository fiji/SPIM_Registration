package task;

import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spim.fiji.plugin.Interest_Point_Registration;
import spim.fiji.plugin.queryXML.HeadlessParseQueryXML;
import spim.fiji.plugin.util.GUIHelper;
import spim.fiji.spimdata.SpimData2;
import spim.process.interestpointregistration.ChannelProcess;
import spim.process.interestpointregistration.RANSACParameters;
import spim.process.interestpointregistration.TransformationModel;
import spim.process.interestpointregistration.geometricdescriptor.RGLDM;
import spim.process.interestpointregistration.geometricdescriptor.RGLDMParameters;
import spim.process.interestpointregistration.geometrichashing.GeometricHashing;
import spim.process.interestpointregistration.geometrichashing.GeometricHashingParameters;
import spim.process.interestpointregistration.icp.IterativeClosestPoint;
import spim.process.interestpointregistration.icp.IterativeClosestPointParameters;
import spim.process.interestpointregistration.optimizationtypes.AllToAllRegistration;
import spim.process.interestpointregistration.optimizationtypes.AllToAllRegistrationWithRange;
import spim.process.interestpointregistration.optimizationtypes.GlobalOptimizationType;
import spim.process.interestpointregistration.optimizationtypes.IndividualTimepointRegistration;
import spim.process.interestpointregistration.optimizationtypes.ReferenceTimepointRegistration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

/**
 * Headless module for Registeration task
 */
public class RegisterationTask extends AbstractTask
{
	// Algorithms:
	//
	// GeometricHashing
	// RGLDM
	// IterativeClosestPoint

	private static final Logger LOG = LoggerFactory.getLogger( RegisterationTask.class );

	public String getTitle() { return "Register Interest Points Task"; }

	public static enum Method { GeometricHashing, RGLDM, IterativeClosestPoint };

	public static enum RegistrationType { TIMEPOINTS_INDIVIDUALLY, TO_REFERENCE_TIMEPOINT, ALL_TO_ALL, ALL_TO_ALL_WITH_RANGE }

	public static enum TransformationModel { Translation, Rigid, Affine }

	public static enum RegularizationModel { Identity, Translation, Rigid, Affine }

	public static class Parameters extends AbstractTask.Parameters
	{
		private Method method;
		private boolean useCluster;

		private RegistrationType type;

		private int referenceTimepointIndex;

		// default - 5
		private int allToAllRange;
		private boolean isConsiderTimepointAsUnit;

		// 0: "Fix first tile",
		// 1: "Select fixed tile",
		// 2: "Do not fix tiles"
		private int fixTilesChoice;

		// 0: "Do not map back (use this if tiles are fixed)",
		// 1: "Map back to first tile using translation model",
		// 2: "Map back to first tile using rigid model",
		// 3: "Map back to user defined tile using translation model",
		// 4: "Map back to user defined tile using rigid model"
		private int mapBackChoice;

		private TransformationModel transformationModel;
		private boolean isRegularized;
		private RegularizationModel regularizationModel;

		// RGLDM parameters
		private int numberOfNeighbors; // 3
		private int redundancy;	// 1

		// RGLDM and GeometricHashing shared parameters
		private float requiredSignificance; // 3 or 10
		private float allowedError;	// 5

		// InteractiveClosestPoint
		private double maxDistance;		// 5
		private int maxIteration;	// 100


		public Method getMethod()
		{
			return method;
		}

		public void setMethod( Method method )
		{
			this.method = method;
		}

		public boolean isUseCluster()
		{
			return useCluster;
		}

		public void setUseCluster( boolean useCluster )
		{
			this.useCluster = useCluster;
		}

		public RegistrationType getType()
		{
			return type;
		}

		public void setType( RegistrationType type )
		{
			this.type = type;
		}

		public int getReferenceTimepointIndex()
		{
			return referenceTimepointIndex;
		}

		public void setReferenceTimepointIndex( int referenceTimepointIndex )
		{
			this.referenceTimepointIndex = referenceTimepointIndex;
		}

		public int getAllToAllRange()
		{
			return allToAllRange;
		}

		public void setAllToAllRange( int allToAllRange )
		{
			this.allToAllRange = allToAllRange;
		}

		public boolean isConsiderTimepointAsUnit()
		{
			return isConsiderTimepointAsUnit;
		}

		public void setConsiderTimepointAsUnit( boolean isConsiderTimepointAsUnit )
		{
			this.isConsiderTimepointAsUnit = isConsiderTimepointAsUnit;
		}

		public int getFixTilesChoice()
		{
			return fixTilesChoice;
		}

		public void setFixTilesChoice( int fixTilesChoice )
		{
			this.fixTilesChoice = fixTilesChoice;
		}

		public int getMapBackChoice()
		{
			return mapBackChoice;
		}

		public void setMapBackChoice( int mapBackChoice )
		{
			this.mapBackChoice = mapBackChoice;
		}

		public TransformationModel getTransformationModel()
		{
			return transformationModel;
		}

		public void setTransformationModel( TransformationModel transformationModel )
		{
			this.transformationModel = transformationModel;
		}

		public boolean isRegularized()
		{
			return isRegularized;
		}

		public void setRegularized( boolean isRegularized )
		{
			this.isRegularized = isRegularized;
		}

		public RegularizationModel getRegularizationModel()
		{
			return regularizationModel;
		}

		public void setRegularizationModel( RegularizationModel regularizationModel )
		{
			this.regularizationModel = regularizationModel;
		}

		public int getNumberOfNeighbors()
		{
			return numberOfNeighbors;
		}

		public void setNumberOfNeighbors( int numberOfNeighbors )
		{
			this.numberOfNeighbors = numberOfNeighbors;
		}

		public int getRedundancy()
		{
			return redundancy;
		}

		public void setRedundancy( int redundancy )
		{
			this.redundancy = redundancy;
		}

		public float getRequiredSignificance()
		{
			return requiredSignificance;
		}

		public void setRequiredSignificance( float requiredSignificance )
		{
			this.requiredSignificance = requiredSignificance;
		}

		public float getAllowedError()
		{
			return allowedError;
		}

		public void setAllowedError( float allowedError )
		{
			this.allowedError = allowedError;
		}

		public double getMaxDistance()
		{
			return maxDistance;
		}

		public void setMaxDistance( double maxDistance )
		{
			this.maxDistance = maxDistance;
		}

		public int getMaxIteration()
		{
			return maxIteration;
		}

		public void setMaxIteration( int maxIteration )
		{
			this.maxIteration = maxIteration;
		}
	}

	public void process( final Parameters params )
	{
		final HeadlessParseQueryXML result = new HeadlessParseQueryXML();
		result.loadXML( params.getXmlFilename(), params.isUseCluster() );

		spimData = result.getData();
		final List< ViewId > viewIdsToProcess = SpimData2.getAllViewIdsSorted( spimData, result.getViewSetupsToProcess(), result.getTimePointsToProcess() );
		final String clusterExtention = result.getClusterExtension();

		// ask which channels have the objects we are searching for
		final List< Channel > channels = SpimData2.getAllChannelsSorted( spimData, viewIdsToProcess );
		final int nAllChannels = spimData.getSequenceDescription().getAllChannelsOrdered().size();

		int[] defaultChannelLabels = null;
		if ( defaultChannelLabels == null || defaultChannelLabels.length != nAllChannels )
			defaultChannelLabels = new int[ nAllChannels ];

		// check which channels and labels are available and build the choices
		final ArrayList< String[] > channelLabels = new ArrayList< String[] >();

		for ( final Channel channel : channels )
		{
			final String[] labels = Interest_Point_Registration.getAllInterestPointLabelsForChannel( spimData, viewIdsToProcess, channel, "register" );

			if ( defaultChannelLabels[ channel.getId() ] >= labels.length )
				defaultChannelLabels[ channel.getId() ] = 0;

			channelLabels.add( labels );
		}

		// assemble which channels have been selected with with label
		final ArrayList< ChannelProcess > channelsToProcess = new ArrayList< ChannelProcess >();
		int i = 0;

		for ( final Channel channel : channels )
		{
			final int channelChoice = defaultChannelLabels[ channel.getId() ];

			if ( channelChoice < channelLabels.get( i ).length - 1 )
			{
				String label = channelLabels.get( i )[ channelChoice ];

				if ( label.contains( Interest_Point_Registration.warningLabel ) )
					label = label.substring( 0, label.indexOf( Interest_Point_Registration.warningLabel ) );

				channelsToProcess.add( new ChannelProcess( channel, label ) );
			}
			++i;
		}

		for ( final ChannelProcess c : channelsToProcess )
			LOG.info( "registering channel: " + c.getChannel().getId() + " label: '" + c.getLabel() + "'" );

		final GlobalOptimizationType type = getGlobalOptimizationType( params, viewIdsToProcess, channelsToProcess );

		if( params.getMethod() != null )
		{
			switch ( params.getMethod() )
			{
				case GeometricHashing:
					processGeometricHashing( params, type, viewIdsToProcess, channelsToProcess  );
					break;
				case RGLDM: // Redundant Geometric Local Descriptor Matching
					processRGLDM( params, type, viewIdsToProcess, channelsToProcess  );
					break;
				case IterativeClosestPoint:
					processInteractiveClosestPoint( params, type, viewIdsToProcess, channelsToProcess );
			}
		}

		// save the XML including transforms and correspondences
		SpimData2.saveXML( spimData, params.getXmlFilename(), clusterExtention );
	}

	private GlobalOptimizationType getGlobalOptimizationType( Parameters params, final List< ViewId > viewIdsToProcess, final ArrayList< ChannelProcess > channelsToProcess )
	{


		GlobalOptimizationType type = null;

		switch ( params.getType() )
		{
			case TIMEPOINTS_INDIVIDUALLY:
				type = new IndividualTimepointRegistration( spimData, viewIdsToProcess, channelsToProcess );
				break;
			case TO_REFERENCE_TIMEPOINT:
				int referenceTimePoint = spimData.getSequenceDescription().getTimePoints().getTimePointsOrdered().get( params.getReferenceTimepointIndex() ).getId();

				// check that at least one of the views of the reference timepoint is part of the viewdescriptions
				boolean contains = false;

				for ( final ViewId viewId : viewIdsToProcess )
					if ( viewId.getTimePointId() == referenceTimePoint )
						contains = true;

				if ( !contains )
				{
					LOG.warn( "No views of the reference timepoint are part of the registration." );
					LOG.warn( "Please re-run and select the corresponding views that should be used as reference." );
				}
				type = new ReferenceTimepointRegistration( spimData, viewIdsToProcess, channelsToProcess, spimData.getSequenceDescription().getTimePoints().getTimePoints().get( params.getReferenceTimepointIndex() ), params.isConsiderTimepointAsUnit() );
				break;
			case ALL_TO_ALL:
				type = new AllToAllRegistration( spimData, viewIdsToProcess, channelsToProcess, params.isConsiderTimepointAsUnit() );
				break;
			case ALL_TO_ALL_WITH_RANGE:
				type = new AllToAllRegistrationWithRange( spimData, viewIdsToProcess, channelsToProcess, params.getAllToAllRange(), params.isConsiderTimepointAsUnit() );
				break;
		}

		// TODO: Reference Tiles Support
		//		if ( !setFixedTilesAndReference( fixTiles, mapBack, type ) )
		//			return;

		return type;
	}

	private void processInteractiveClosestPoint( final Parameters params, final GlobalOptimizationType type, final List< ViewId > viewIdsToProcess, final ArrayList< ChannelProcess > channelsToProcess )
	{
		IterativeClosestPoint icp = new IterativeClosestPoint( spimData, viewIdsToProcess, channelsToProcess );

		switch ( params.getTransformationModel() )
		{
			case Translation: icp.setModel( new spim.process.interestpointregistration.TransformationModel( 0 ) );
				break;
			case Rigid: icp.setModel( new spim.process.interestpointregistration.TransformationModel( 1 ) );
				break;
			case Affine: icp.setModel( new spim.process.interestpointregistration.TransformationModel( 2 ) );
				break;
		}

		if ( params.isRegularized() )
		{
			icp.getModel().setRegularize( true );
			switch ( params.getRegularizationModel() )
			{
				case Identity: icp.getModel().setRegularizedModelIndex( 0 );
					break;
				case Translation: icp.getModel().setRegularizedModelIndex( 1 );
					break;
				case Rigid: icp.getModel().setRegularizedModelIndex( 2 );
					break;
				case Affine: icp.getModel().setRegularizedModelIndex( 3 );
					break;
			}
		}

		icp.setParameters( new IterativeClosestPointParameters( params.getMaxDistance(), params.getMaxIteration() ) );

		icp.register( type, true, true );
	}

	private void processRGLDM( final Parameters params, final GlobalOptimizationType type, final List< ViewId > viewIdsToProcess, final ArrayList< ChannelProcess > channelsToProcess  )
	{
		RGLDM rgldm = new RGLDM( spimData, viewIdsToProcess, channelsToProcess );

		switch ( params.getTransformationModel() )
		{
			case Translation: rgldm.setModel( new spim.process.interestpointregistration.TransformationModel( 0 ) );
				break;
			case Rigid: rgldm.setModel( new spim.process.interestpointregistration.TransformationModel( 1 ) );
				break;
			case Affine: rgldm.setModel( new spim.process.interestpointregistration.TransformationModel( 2 ) );
				break;
		}

		if ( params.isRegularized() )
		{
			rgldm.getModel().setRegularize( true );
			switch ( params.getRegularizationModel() )
			{
				case Identity: rgldm.getModel().setRegularizedModelIndex( 0 );
					break;
				case Translation: rgldm.getModel().setRegularizedModelIndex( 1 );
					break;
				case Rigid: rgldm.getModel().setRegularizedModelIndex( 2 );
					break;
				case Affine: rgldm.getModel().setRegularizedModelIndex( 3 );
					break;
			}
		}

		rgldm.setParameters( new RGLDMParameters( RGLDMParameters.differenceThreshold, params.getRequiredSignificance(), params.getNumberOfNeighbors(), params.getRedundancy() ) );

		rgldm.setRansacParams( new RANSACParameters( params.getAllowedError(), RANSACParameters.min_inlier_ratio, RANSACParameters.min_inlier_factor, RANSACParameters.num_iterations ) );

		rgldm.register( type, true, true );
	}

	private void processGeometricHashing( final Parameters params, final GlobalOptimizationType type, final List< ViewId > viewIdsToProcess, final ArrayList< ChannelProcess > channelsToProcess  )
	{
		GeometricHashing gh = new GeometricHashing( spimData, viewIdsToProcess, channelsToProcess );

		switch ( params.getTransformationModel() )
		{
			case Translation: gh.setModel( new spim.process.interestpointregistration.TransformationModel( 0 ) );
				break;
			case Rigid: gh.setModel( new spim.process.interestpointregistration.TransformationModel( 1 ) );
				break;
			case Affine: gh.setModel( new spim.process.interestpointregistration.TransformationModel( 2 ) );
				break;
		}

		if ( params.isRegularized() )
		{
			gh.getModel().setRegularize( true );
			switch ( params.getRegularizationModel() )
			{
				case Identity: gh.getModel().setRegularizedModelIndex( 0 );
					break;
				case Translation: gh.getModel().setRegularizedModelIndex( 1 );
					break;
				case Rigid: gh.getModel().setRegularizedModelIndex( 2 );
					break;
				case Affine: gh.getModel().setRegularizedModelIndex( 3 );
					break;
			}
		}

		gh.setRansacParams( new RANSACParameters( params.getAllowedError(), RANSACParameters.min_inlier_ratio, RANSACParameters.min_inlier_factor, RANSACParameters.num_iterations ) );

		gh.setGhParams( new GeometricHashingParameters( GeometricHashingParameters.differenceThreshold, params.getRequiredSignificance(), GeometricHashingParameters.useAssociatedBeads ) );

		gh.register( type, true, true );
	}

	private Parameters getParams( final String[] args )
	{
		final Properties props = parseArgument( "RegistrationTask", getTitle(), args );

		final Parameters params = new Parameters();
		params.setXmlFilename( props.getProperty( "xml_filename" ) );
		params.setUseCluster( Boolean.parseBoolean( props.getProperty( "use_cluster", "false" ) ) );

		params.setReferenceTimepointIndex( Integer.parseInt( props.getProperty( "reference_timepoint", "0" ) ) );

		params.setAllToAllRange( Integer.parseInt( props.getProperty( "Range_for_all-to-all_timepoint_matching", "5" ) ) );

		params.setConsiderTimepointAsUnit( Boolean.parseBoolean( props.getProperty( "consider_each_timepoint_as_rigid_unit", "false" ) ) );

		final String registrationType = props.getProperty( "type_of_registration" );
		if ( registrationType.equals( "TimepointsIndividually" ) )
		{
			params.setType( RegistrationType.TIMEPOINTS_INDIVIDUALLY );
		}
		else if( registrationType.equals( "ToReferenceTimepoint" ) )
		{
			params.setType( RegistrationType.TO_REFERENCE_TIMEPOINT );
		}
		else if( registrationType.equals( "AllToAll" ) )
		{
			params.setType( RegistrationType.ALL_TO_ALL );
		}
		else if( registrationType.equals( "AllToAllWithRange" ) )
		{
			params.setType( RegistrationType.ALL_TO_ALL_WITH_RANGE );
		}

		final String method = props.getProperty( "method" );

		if ( method.equals( "IterativeClosestPoint" ) )
		{
			params.setMethod( Method.IterativeClosestPoint );
		}
		else if ( method.equals( "RGLDM" ))
		{
			params.setMethod( Method.IterativeClosestPoint );
			// RGLDM and GeometricHashing shared parameters. RGLDM's default value is 3
			params.setRequiredSignificance( Float.parseFloat( props.getProperty( "required_significance", "3" ) ) );
		}
		else if ( method.equals( "GeometricHashing" ))
		{
			params.setMethod( Method.GeometricHashing );
			// RGLDM and GeometricHashing shared parameters. GeometricHashing's default value is 10
			params.setRequiredSignificance( Float.parseFloat( props.getProperty( "required_significance", "10" ) ) );
		}

		final String transformationModel = props.getProperty( "transformation_model", "Affine" );
		if ( transformationModel.equals( "Translation" ) )
		{
			params.setTransformationModel( TransformationModel.Translation );
		}
		else if ( transformationModel.equals( "Rigid" ) )
		{
			params.setTransformationModel( TransformationModel.Rigid );
		}
		else if ( transformationModel.equals( "Affine" ) )
		{
			params.setTransformationModel( TransformationModel.Affine );
		}

		params.setRegularized( Boolean.parseBoolean( props.getProperty( "is_regularised" ) ) );

		if ( params.isRegularized() )
		{
			final String regularizationModel = props.getProperty( "regularization_model", "Rigid" );
			if ( regularizationModel.equals( "Identity" ) )
			{
				params.setRegularizationModel( RegularizationModel.Identity );
			}
			else if ( regularizationModel.equals( "Translation" ) )
			{
				params.setRegularizationModel( RegularizationModel.Translation );
			}
			else if ( regularizationModel.equals( "Rigid" ) )
			{
				params.setRegularizationModel( RegularizationModel.Rigid );
			}
			else if ( regularizationModel.equals( "Affine" ) )
			{
				params.setRegularizationModel( RegularizationModel.Affine );
			}
		}

		// RGLDM parameters
		params.setNumberOfNeighbors( Integer.parseInt( props.getProperty( "number_of_neighbors", "3" ) ) );
		params.setRedundancy( Integer.parseInt( props.getProperty( "redundancy", "1" ) ) );

		// RGLDM and GeometricHashing shared parameters
		params.setAllowedError( Float.parseFloat( props.getProperty( "allowed_error", "5" ) ));

		// InteractiveClosestPoint
		params.setMaxDistance( Double.parseDouble( props.getProperty( "max_distance", "5" ) ) );
		params.setMaxIteration( Integer.parseInt( props.getProperty( "max_iteration", "100" ) ) );

		return params;
	}

	@Override public void process( String[] args )
	{
		process( getParams( args ) );
	}

	public static void main( String[] argv )
	{
		// Test mvn commamnd
		//
		// module load cuda/6.5.14
		// export MAVEN_OPTS="-Xms4g -Xmx16g -Djava.awt.headless=true"
		// mvn exec:java -Dexec.mainClass="task.RegisterationTask" -Dexec.args="-Dxml_filename=/projects/pilot_spim/moon/test.xml -Dmethod=GeometricHashing -Dtype_of_registration=TimepointsIndividually"
		RegisterationTask task = new RegisterationTask();
		task.process( argv );
		System.exit( 0 );
	}
}
