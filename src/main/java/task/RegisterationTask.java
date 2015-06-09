package task;

import mpicbg.models.RigidModel3D;
import mpicbg.models.TranslationModel3D;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.io.IOFunctions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spim.fiji.plugin.Interest_Point_Registration;
import spim.fiji.plugin.interestpointregistration.InterestPointRegistration;
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
import spim.process.interestpointregistration.optimizationtypes.GlobalOptimizationSubset;
import spim.process.interestpointregistration.optimizationtypes.GlobalOptimizationType;
import spim.process.interestpointregistration.optimizationtypes.IndividualTimepointRegistration;
import spim.process.interestpointregistration.optimizationtypes.ReferenceTimepointRegistration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

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

	/**
	 * Gets title.
	 *
	 * @return the title
	 */
	public String getTitle() { return "Register Interest Points Task"; }

	/**
	 * The enum Method.
	 */
	public static enum Method {
		/**
		 * The GeometricHashing.
		 */
		GeometricHashing,
		/**
		 * The RGLDM.
		 */
		RGLDM,
		/**
		 * The IterativeClosestPoint.
		 */
		IterativeClosestPoint };

	/**
	 * The enum Registration type.
	 */
	public static enum RegistrationType {
		/**
		 * The TIMEPOINTS_INDIVIDUALLY.
		 */
		TIMEPOINTS_INDIVIDUALLY,
		/**
		 * The TO_REFERENCE_TIMEPOINT.
		 */
		TO_REFERENCE_TIMEPOINT,
		/**
		 * The ALL_TO_ALL.
		 */
		ALL_TO_ALL,
		/**
		 * The ALL_TO_ALL_WITH_RANGE.
		 */
		ALL_TO_ALL_WITH_RANGE }

	/**
	 * The enum Transformation model.
	 */
	public static enum TransformationModel {
		/**
		 * The Translation.
		 */
		Translation,
		/**
		 * The Rigid.
		 */
		Rigid,
		/**
		 * The Affine.
		 */
		Affine }

	/**
	 * The enum Regularization model.
	 */
	public static enum RegularizationModel {
		/**
		 * The Identity.
		 */
		Identity,
		/**
		 * The Translation.
		 */
		Translation,
		/**
		 * The Rigid.
		 */
		Rigid,
		/**
		 * The Affine.
		 */
		Affine }

	/**
	 * The type Parameters.
	 */
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

		/**
		 * Gets method.
		 *
		 * @return the method
		 */
		public Method getMethod()
		{
			return method;
		}

		/**
		 * Sets method.
		 *
		 * @param method the method
		 */
		public void setMethod( Method method )
		{
			this.method = method;
		}

		/**
		 * Is use cluster.
		 *
		 * @return the boolean
		 */
		public boolean isUseCluster()
		{
			return useCluster;
		}

		/**
		 * Sets use cluster.
		 *
		 * @param useCluster the use cluster
		 */
		public void setUseCluster( boolean useCluster )
		{
			this.useCluster = useCluster;
		}

		/**
		 * Gets type.
		 *
		 * @return the type
		 */
		public RegistrationType getType()
		{
			return type;
		}

		/**
		 * Sets type.
		 *
		 * @param type the type
		 */
		public void setType( RegistrationType type )
		{
			this.type = type;
		}

		/**
		 * Gets reference timepoint index.
		 *
		 * @return the reference timepoint index
		 */
		public int getReferenceTimepointIndex()
		{
			return referenceTimepointIndex;
		}

		/**
		 * Sets reference timepoint index.
		 *
		 * @param referenceTimepointIndex the reference timepoint index
		 */
		public void setReferenceTimepointIndex( int referenceTimepointIndex )
		{
			this.referenceTimepointIndex = referenceTimepointIndex;
		}

		/**
		 * Gets all to all range.
		 *
		 * @return the all to all range
		 */
		public int getAllToAllRange()
		{
			return allToAllRange;
		}

		/**
		 * Sets all to all range.
		 *
		 * @param allToAllRange the all to all range
		 */
		public void setAllToAllRange( int allToAllRange )
		{
			this.allToAllRange = allToAllRange;
		}

		/**
		 * Is consider timepoint as unit.
		 *
		 * @return the boolean
		 */
		public boolean isConsiderTimepointAsUnit()
		{
			return isConsiderTimepointAsUnit;
		}

		/**
		 * Sets consider timepoint as unit.
		 *
		 * @param isConsiderTimepointAsUnit the is consider timepoint as unit
		 */
		public void setConsiderTimepointAsUnit( boolean isConsiderTimepointAsUnit )
		{
			this.isConsiderTimepointAsUnit = isConsiderTimepointAsUnit;
		}

		/**
		 * Gets fix tiles choice.
		 *
		 * @return the fix tiles choice
		 */
		public int getFixTilesChoice()
		{
			return fixTilesChoice;
		}

		/**
		 * Sets fix tiles choice.
		 *
		 * @param fixTilesChoice the fix tiles choice
		 */
		public void setFixTilesChoice( int fixTilesChoice )
		{
			this.fixTilesChoice = fixTilesChoice;
		}

		/**
		 * Gets map back choice.
		 *
		 * @return the map back choice
		 */
		public int getMapBackChoice()
		{
			return mapBackChoice;
		}

		/**
		 * Sets map back choice.
		 *
		 * @param mapBackChoice the map back choice
		 */
		public void setMapBackChoice( int mapBackChoice )
		{
			this.mapBackChoice = mapBackChoice;
		}

		/**
		 * Gets transformation model.
		 *
		 * @return the transformation model
		 */
		public TransformationModel getTransformationModel()
		{
			return transformationModel;
		}

		/**
		 * Sets transformation model.
		 *
		 * @param transformationModel the transformation model
		 */
		public void setTransformationModel( TransformationModel transformationModel )
		{
			this.transformationModel = transformationModel;
		}

		/**
		 * Is regularized.
		 *
		 * @return the boolean
		 */
		public boolean isRegularized()
		{
			return isRegularized;
		}

		/**
		 * Sets regularized.
		 *
		 * @param isRegularized the is regularized
		 */
		public void setRegularized( boolean isRegularized )
		{
			this.isRegularized = isRegularized;
		}

		/**
		 * Gets regularization model.
		 *
		 * @return the regularization model
		 */
		public RegularizationModel getRegularizationModel()
		{
			return regularizationModel;
		}

		/**
		 * Sets regularization model.
		 *
		 * @param regularizationModel the regularization model
		 */
		public void setRegularizationModel( RegularizationModel regularizationModel )
		{
			this.regularizationModel = regularizationModel;
		}

		/**
		 * Gets number of neighbors.
		 *
		 * @return the number of neighbors
		 */
		public int getNumberOfNeighbors()
		{
			return numberOfNeighbors;
		}

		/**
		 * Sets number of neighbors.
		 *
		 * @param numberOfNeighbors the number of neighbors
		 */
		public void setNumberOfNeighbors( int numberOfNeighbors )
		{
			this.numberOfNeighbors = numberOfNeighbors;
		}

		/**
		 * Gets redundancy.
		 *
		 * @return the redundancy
		 */
		public int getRedundancy()
		{
			return redundancy;
		}

		/**
		 * Sets redundancy.
		 *
		 * @param redundancy the redundancy
		 */
		public void setRedundancy( int redundancy )
		{
			this.redundancy = redundancy;
		}

		/**
		 * Gets required significance.
		 *
		 * @return the required significance
		 */
		public float getRequiredSignificance()
		{
			return requiredSignificance;
		}

		/**
		 * Sets required significance.
		 *
		 * @param requiredSignificance the required significance
		 */
		public void setRequiredSignificance( float requiredSignificance )
		{
			this.requiredSignificance = requiredSignificance;
		}

		/**
		 * Gets allowed error.
		 *
		 * @return the allowed error
		 */
		public float getAllowedError()
		{
			return allowedError;
		}

		/**
		 * Sets allowed error.
		 *
		 * @param allowedError the allowed error
		 */
		public void setAllowedError( float allowedError )
		{
			this.allowedError = allowedError;
		}

		/**
		 * Gets max distance.
		 *
		 * @return the max distance
		 */
		public double getMaxDistance()
		{
			return maxDistance;
		}

		/**
		 * Sets max distance.
		 *
		 * @param maxDistance the max distance
		 */
		public void setMaxDistance( double maxDistance )
		{
			this.maxDistance = maxDistance;
		}

		/**
		 * Gets max iteration.
		 *
		 * @return the max iteration
		 */
		public int getMaxIteration()
		{
			return maxIteration;
		}

		/**
		 * Sets max iteration.
		 *
		 * @param maxIteration the max iteration
		 */
		public void setMaxIteration( int maxIteration )
		{
			this.maxIteration = maxIteration;
		}
	}

	/**
	 * Task Process with the parsed params.
	 *
	 * @param params the params
	 */
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

	private GlobalOptimizationType getGlobalOptimizationType( final Parameters params, final List< ViewId > viewIdsToProcess, final ArrayList< ChannelProcess > channelsToProcess )
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

		final List< GlobalOptimizationSubset > subsets = type.getAllViewPairs();

		final Set< ViewId > fixedTiles = new HashSet< ViewId >();

		for ( final GlobalOptimizationSubset subset : subsets )
		{
			if ( subset.getViews().size() == 0 )
				LOG.info( "Nothing to do for: " + subset.getDescription() + ". No tiles fixed." );
			else
				fixedTiles.add( subset.getViews().get( 0 ) );
		}

		type.setFixedTiles( fixedTiles );

		LOG.info( "Following tiles are fixed:" );
		for ( final ViewId id : type.getFixedTiles() )
		{
			final ViewDescription vd = type.getSpimData().getSequenceDescription().getViewDescription( id );
			final ViewSetup vs = vd.getViewSetup();

			LOG.info( "Angle:" + vs.getAngle().getName() + " Channel:" + vs.getChannel().getName() + " Illum:" + vs.getIllumination().getName() + " TimePoint:" + vd.getTimePoint().getId() );
		}

		if ( params.getMapBackChoice() == 0 )
		{
			type.setMapBackModel( null );
			type.setMapBackReferenceTiles( new HashMap< GlobalOptimizationSubset, ViewId >() );
		}
		else if ( params.getMapBackChoice() == 1 || params.getMapBackChoice() == 3 )
		{
			type.setMapBackModel( new TranslationModel3D() );
		}
		else
		{
			type.setMapBackModel( new RigidModel3D() );
		}

		if ( params.getMapBackChoice() == 1 || params.getMapBackChoice() == 2 )
		{
			for ( final GlobalOptimizationSubset subset : subsets )
				type.setMapBackReferenceTile( subset, subset.getViews().get( 0 ) );
		}

		LOG.info( "Following tiles are reference tiles (for mapping back if there are no fixed tiles):" );
		for ( final GlobalOptimizationSubset subset : subsets )
		{
			final ViewId id = type.getMapBackReferenceTile( subset );
			if ( id != null )
			{
				final ViewDescription vd = type.getSpimData().getSequenceDescription().getViewDescription( id );
				final ViewSetup vs = vd.getViewSetup();

				LOG.info( "Angle:" + vs.getAngle().getName() + " Channel:" + vs.getChannel().getName() + " Illum:" + vs.getIllumination().getName() + " TimePoint:" + vd.getTimePoint().getId() );
			}
		}

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

		icp.register( type, true, false );
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

		rgldm.register( type, true, false );
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

		gh.register( type, true, false );
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

		params.setType( RegistrationType.valueOf( props.getProperty( "type_of_registration" ) ) );

		params.setMethod( Method.valueOf( props.getProperty( "method" ) ) );

		switch ( params.getMethod() )
		{
			case IterativeClosestPoint:
				// InteractiveClosestPoint
				params.setMaxDistance( Double.parseDouble( props.getProperty( "max_distance", "5" ) ) );
				params.setMaxIteration( Integer.parseInt( props.getProperty( "max_iteration", "100" ) ) );
				break;
			case RGLDM:
				// RGLDM and GeometricHashing shared parameters. RGLDM's default value is 3
				params.setRequiredSignificance( Float.parseFloat( props.getProperty( "required_significance", "3" ) ) );
				params.setAllowedError( Float.parseFloat( props.getProperty( "allowed_error", "5" ) ));
				break;
			case GeometricHashing:
				params.setNumberOfNeighbors( Integer.parseInt( props.getProperty( "number_of_neighbors", "3" ) ) );
				params.setRedundancy( Integer.parseInt( props.getProperty( "redundancy", "1" ) ) );
				// RGLDM and GeometricHashing shared parameters. GeometricHashing's default value is 10
				params.setRequiredSignificance( Float.parseFloat( props.getProperty( "required_significance", "10" ) ) );
				params.setAllowedError( Float.parseFloat( props.getProperty( "allowed_error", "5" ) ));
				break;
		}

		params.setTransformationModel( TransformationModel.valueOf( props.getProperty( "transformation_model", "Affine" ) ) );

		params.setRegularized( Boolean.parseBoolean( props.getProperty( "is_regularised" ) ) );

		if ( params.isRegularized() )
		{
			params.setRegularizationModel( RegularizationModel.valueOf( props.getProperty( "regularization_model", "Rigid" ) ) );
		}

		return params;
	}

	@Override public void process( final String[] args )
	{
		process( getParams( args ) );
	}

	/**
	 * The entry point of application.
	 *
	 * @param argv the input arguments
	 */
	public static void main( final String[] argv )
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
