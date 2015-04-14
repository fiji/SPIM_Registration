///////////////////////////////////////////////////////////////////////////////
//FILE:          MultipageTiffReader.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com, 2012
//
// COPYRIGHT:    University of California, San Francisco, 2012
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
package spim.fiji.spimdata.imgloaders;

import ij.ImageJ;
import ij.ImagePlus;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import mpicbg.spim.io.IOFunctions;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.util.Util;
import spim.fiji.ImgLib2Temp.Pair;
import spim.fiji.ImgLib2Temp.ValuePair;

public class MultipageTiffReader
{
	private static final long BIGGEST_INT_BIT = (long) Math.pow(2, 31);

	public static final int INDEX_MAP_HEADER = 3453623;
	public static final int DISPLAY_SETTINGS_OFFSET_HEADER = 483765892;
	public static final int DISPLAY_SETTINGS_HEADER = 347834724;
	public static final int INDEX_MAP_OFFSET_HEADER = 54773648;
	public static final int SUMMARY_MD_HEADER = 2355492;
	public static final int COMMENTS_OFFSET_HEADER = 99384722;
	public static final int COMMENTS_HEADER = 84720485;

	public static final char BITS_PER_SAMPLE = 258;
	public static final char STRIP_OFFSETS = 273;
	public static final char SAMPLES_PER_PIXEL = 277;
	public static final char STRIP_BYTE_COUNTS = 279;
	public static final char IMAGE_DESCRIPTION = 270;

	public static final char MM_METADATA = 51123;

	private ByteOrder byteOrder_;
	private File file_;
	private RandomAccessFile raFile_;
	private FileChannel fileChannel_;

	/*
	 * Contains pixel size, etc.
	 */
	private HashMap< String, Object > summaryMetadata_;
	private int byteDepth_ = 0;;
	private boolean rgb_;

	protected String unit = "um";
	protected double calX = Double.NaN;
	protected double calY = Double.NaN;
	protected double calZ = Double.NaN;
	protected double[] rotAxis = null;
	protected boolean applyAxis = true;

	protected List< String > angleNames = null;
	protected List< String > channelNames = null;
	
	private HashMap< String, Long > indexMap_;

	/**
	 * This constructor is used for opening datasets that have already been
	 * saved
	 */
	public MultipageTiffReader( final File file ) throws IOException
	{
		this.file_ = file;

		try
		{
			this.raFile_ = new RandomAccessFile( file_, "rw" );
			this.fileChannel_ = raFile_.getChannel();
		}
		catch ( Exception e )
		{
			e.printStackTrace();
			throw new IOException( "Can't successfully open file: " + file_.getName() + ": " + e );
		}

		readHeader();
		summaryMetadata_ = readSummaryMD();

		if ( summaryMetadata_ == null )
			throw new IOException( "Could not read metadata" );

		try
		{
			readIndexMap();
		}
		catch (Exception e)
		{
			e.printStackTrace();
			throw new IOException( "Reading of dataset unsuccessful for file: " + file_.getName() );
		}
	}

	public String getPixelType()
	{
		try
		{
			if ( summaryMetadata_ != null )
				return summaryMetadata_.get( "PixelType" ).toString();
		}
		catch ( Exception e)
		{
			try
			{
				int ijType = Integer.parseInt( summaryMetadata_.get( "IJType" ).toString() );
				if (ijType == ImagePlus.GRAY8) {
					return "GRAY8";
				} else if (ijType == ImagePlus.GRAY16) {
					return "GRAY16";
				} else if (ijType == ImagePlus.GRAY32) {
					return "GRAY32";
				} else if (ijType == ImagePlus.COLOR_RGB) {
					return "RGB32";
				} else {
					throw new RuntimeException("Can't figure out pixel type");
				}
				// There is no IJType for RGB64.
			} catch ( Exception e2 ) {
				throw new RuntimeException("Can't figure out pixel type");
			}
		}
		return "";
	}

	public void setAngleNames( final List< String > names ) { this.angleNames = names; }
	public void setChannelNames( final List< String > names ) { this.channelNames = names; }
	
	private void getRGBAndByteDepth( final HashMap< String, Object > map )
	{
		try {
			String pixelType = getPixelType();
			rgb_ = pixelType.startsWith("RGB");

			if (pixelType.equals("RGB32") || pixelType.equals("GRAY8")) {
				byteDepth_ = 1;
			} else {
				byteDepth_ = 2;
			}
		} catch (Exception ex) {
			IOFunctions.println(ex);
		}
	}

	public HashMap< String, Object > getSummaryMetadata() { return summaryMetadata_; }

	public Pair<Object, HashMap< String, Object > > readImage( final String label )
	{
		if (indexMap_.containsKey(label))
		{
			if (fileChannel_ == null)
			{
				IOFunctions.println("Attempted to read image on FileChannel that is null");
				return null;
			}
			try
			{
				long byteOffset = indexMap_.get(label);

				IFDData data = readIFD(byteOffset);
				return readTaggedImage(data);
			}
			catch (IOException ex)
			{
				IOFunctions.println(ex);
				return null;
			}
		}
		else
		{
			// label not in map--either writer hasnt finished writing it
			return null;
		}
	}

	public Set<String> getIndexKeys() {
		if (indexMap_ == null)
			return null;
		return indexMap_.keySet();
	}

	private HashMap< String, Object > readSummaryMD()
	{
		try
		{
			ByteBuffer mdInfo = ByteBuffer.allocate(8).order(byteOrder_);
			fileChannel_.read(mdInfo, 32);
			int header = mdInfo.getInt(0);
			int length = mdInfo.getInt(4);

			if (header != SUMMARY_MD_HEADER) {
				IOFunctions.println("Summary Metadata Header Incorrect");
				return null;
			}

			ByteBuffer mdBuffer = ByteBuffer.allocate(length).order(byteOrder_);
			fileChannel_.read(mdBuffer, 40);

			HashMap< String, Object > summaryMD = parseJSONSimple( getString( mdBuffer ) );

			if ( summaryMD == null )
				IOFunctions.println( "Couldn't read summary Metadata from file: " + file_.getName());

			// MVRotationAxis = 0_1_0
			// MVRotations = 0_90_0_90
			// >>> channels increasing, angles increasing faster

			// fake the multiviewdata if necessary
			if ( !summaryMD.containsKey( "MVRotationAxis" ) )
				summaryMD.put( "MVRotationAxis", "0_1_0" );

			if ( !summaryMD.containsKey( "MVRotations" ) )
			{
				final int numChannels = Integer.parseInt( summaryMD.get( "Channels" ).toString() );

				if ( numChannels == 2 )
					summaryMD.put( "MVRotations", "0_90" );
				else if ( numChannels == 4 )
					summaryMD.put( "MVRotations", "0_90_0_90" );
				else if ( numChannels == 6 )
					summaryMD.put( "MVRotations", "0_90_0_90_0_90" );
			}

			return summaryMD;
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
			IOFunctions.println( "Couldn't read summary Metadata from file: " + file_.getName());
			return null;
		}
	}

	protected HashMap< String, Object > parseJSONSimple( final String json )
	{
		String jsonString = json.trim();
		jsonString = jsonString.substring( 1, jsonString.length() - 1 );

		final HashMap< String, Object > map = new HashMap< String, Object >();

		do
		{
			if ( !jsonString.startsWith( "\"" ) )
			{
				IOFunctions.println( "Failed to parse json string: " + json );
				return null;
			}

			final String key = jsonString.substring( 1, jsonString.indexOf( '\"', 1 ) );
			
			int valueStart = jsonString.indexOf( "\":" ) + 2;
			int valueEnd;

			if ( jsonString.charAt( valueStart ) == '[' )
			{
				valueEnd = jsonString.indexOf( ']' ) + 1;
			}
			else if ( jsonString.charAt( valueStart ) == '\"' )
			{
				++valueStart;
				valueEnd = jsonString.indexOf( '\"', valueStart );
			}
			else
			{
				valueEnd = jsonString.indexOf( ',' );
				if ( valueEnd == -1 )
					valueEnd = jsonString.length();
			}

			final String value = jsonString.substring( valueStart, valueEnd );

			final int nextComma = jsonString.indexOf( ',', valueEnd );

			if ( nextComma == -1 )
				jsonString = "";
			else
				jsonString = jsonString.substring( nextComma + 1, jsonString.length() );

			map.put( key, value );
		}
		while ( jsonString.length() > 0 );

		return map;
	}

	private ByteBuffer readIntoBuffer(long position, int length)
			throws IOException {
		ByteBuffer buffer = ByteBuffer.allocate(length).order(byteOrder_);
		fileChannel_.read(buffer, position);
		return buffer;
	}

	private long readOffsetHeaderAndOffset(int offsetHeaderVal, int startOffset)
			throws IOException {
		ByteBuffer buffer1 = readIntoBuffer(startOffset, 8);
		int offsetHeader = buffer1.getInt(0);
		if (offsetHeader != offsetHeaderVal) {
			throw new IOException("Offset header incorrect, expected: "
					+ offsetHeaderVal + "   found: " + offsetHeader);
		}
		return unsignInt(buffer1.getInt(4));
	}

	public static String generateLabel( final int channel, final int slice, final int frame, final int position )
	{
		return NumberUtils.intToCoreString(channel) + "_"
				+ NumberUtils.intToCoreString(slice) + "_"
				+ NumberUtils.intToCoreString(frame) + "_"
				+ NumberUtils.intToCoreString(position);
	}

	private void readIndexMap() throws IOException {
		long offset = readOffsetHeaderAndOffset(INDEX_MAP_OFFSET_HEADER, 8);
		ByteBuffer header = readIntoBuffer(offset, 8);
		if (header.getInt(0) != INDEX_MAP_HEADER) {
			throw new RuntimeException("Error reading index map header");
		}
		int numMappings = header.getInt(4);
		indexMap_ = new HashMap<String, Long>();
		ByteBuffer mapBuffer = readIntoBuffer(offset + 8, 20 * numMappings);
		for (int i = 0; i < numMappings; i++) {
			int channel = mapBuffer.getInt(i * 20);
			int slice = mapBuffer.getInt(i * 20 + 4);
			int frame = mapBuffer.getInt(i * 20 + 8);
			int position = mapBuffer.getInt(i * 20 + 12);
			long imageOffset = unsignInt(mapBuffer.getInt(i * 20 + 16));
			if (imageOffset == 0) {
				break; // end of index map reached
			}
			// If a duplicate label is read, forget about the previous one
			// if data has been intentionally overwritten, this gives the most
			// current version
			indexMap_.put(generateLabel(channel, slice, frame, position),
					imageOffset);
		}
	}

	private IFDData readIFD(long byteOffset) throws IOException {
		ByteBuffer buff = readIntoBuffer(byteOffset, 2);
		int numEntries = buff.getChar(0);

		ByteBuffer entries = readIntoBuffer(byteOffset + 2, numEntries * 12 + 4).order(byteOrder_);
		IFDData data = new IFDData();
		for (int i = 0; i < numEntries; i++) {
			IFDEntry entry = readDirectoryEntry(i * 12, entries);
			if (entry.tag == MM_METADATA) {
				data.mdOffset = entry.value;
				data.mdLength = entry.count;
			} else if (entry.tag == STRIP_OFFSETS) {
				data.pixelOffset = entry.value;
			} else if (entry.tag == STRIP_BYTE_COUNTS) {
				data.bytesPerImage = entry.value;
			}
		}
		data.nextIFD = unsignInt(entries.getInt(numEntries * 12));
		data.nextIFDOffsetLocation = byteOffset + 2 + numEntries * 12;
		return data;
	}

	private String getString(ByteBuffer buffer) {
		try {
			return new String(buffer.array(), "UTF-8");
		} catch (UnsupportedEncodingException ex) {
			IOFunctions.println(ex);
			return "";
		}
	}

	private Pair< Object, HashMap< String, Object > > readTaggedImage( final IFDData data ) throws IOException
	{
		ByteBuffer pixelBuffer = ByteBuffer.allocate((int) data.bytesPerImage).order( byteOrder_ );
		ByteBuffer mdBuffer = ByteBuffer.allocate((int) data.mdLength).order( byteOrder_ );
		fileChannel_.read(pixelBuffer, data.pixelOffset);
		fileChannel_.read(mdBuffer, data.mdOffset);

		final HashMap< String, Object > md = parseJSONSimple( getString( mdBuffer ) );

		if (byteDepth_ == 0) {
			getRGBAndByteDepth(md);
		}

		if (rgb_)
		{
			IOFunctions.println( "RGB types not supported." );
			return null;
		}
		else
		{
			if (byteDepth_ == 1)
			{
				return new ValuePair<Object, HashMap< String, Object >>( pixelBuffer.array(), md );
			}
			else
			{
				short[] pix = new short[pixelBuffer.capacity() / 2];
				for (int i = 0; i < pix.length; i++) {
					pix[i] = pixelBuffer.getShort(i * 2);
				}
				return new ValuePair<Object, HashMap< String, Object >>(pix, md);
			}
		}
	}

	private IFDEntry readDirectoryEntry(int offset, ByteBuffer buffer)
			throws IOException {
		char tag = buffer.getChar(offset);
		char type = buffer.getChar(offset + 2);
		long count = unsignInt(buffer.getInt(offset + 4));
		long value;
		if (type == 3 && count == 1) {
			value = buffer.getChar(offset + 8);
		} else {
			value = unsignInt(buffer.getInt(offset + 8));
		}
		return (new IFDEntry(tag, type, count, value));
	}

	// returns byteoffset of first IFD
	private long readHeader() throws IOException {
		ByteBuffer tiffHeader = ByteBuffer.allocate(8);
		fileChannel_.read(tiffHeader, 0);
		char zeroOne = tiffHeader.getChar(0);
		if (zeroOne == 0x4949) {
			byteOrder_ = ByteOrder.LITTLE_ENDIAN;
		} else if (zeroOne == 0x4d4d) {
			byteOrder_ = ByteOrder.BIG_ENDIAN;
		} else {
			throw new IOException("Error reading Tiff header");
		}
		tiffHeader.order(byteOrder_);
		short twoThree = tiffHeader.getShort(2);
		if (twoThree != 42) {
			throw new IOException("Tiff identifier code incorrect");
		}
		return unsignInt(tiffHeader.getInt(4));
	}

	public void close() throws IOException {
		if (fileChannel_ != null) {
			fileChannel_.close();
			fileChannel_ = null;
		}
		if (raFile_ != null) {
			raFile_.close();
			raFile_ = null;
		}
	}

	public void setApplyAxis( final boolean apply ) { this.applyAxis = apply; }
	public boolean applyAxis() { return applyAxis; }
	public void setCalX( final double cal ) { this.calX = cal; }
	public void setCalY( final double cal ) { this.calY = cal; }
	public void setCalZ( final double cal ) { this.calZ = cal; }
	public void setCalUnit( final String unit ) { this.unit = unit; }
	
	public int width() { return Integer.parseInt( summaryMetadata_.get( "Width" ).toString() ); }
	public int height() { return Integer.parseInt( summaryMetadata_.get( "Height" ).toString() ); }
	public int depth() { return Integer.parseInt( summaryMetadata_.get( "Slices" ).toString() ); }
	public double calX()
	{
		if ( Double.isNaN( calX ) )
			return Double.parseDouble( summaryMetadata_.get( "PixelSize_um" ).toString() );
		else
			return calX;
	}
	public double calY()
	{
		if ( Double.isNaN( calY ) )
			return Double.parseDouble( summaryMetadata_.get( "PixelSize_um" ).toString() );
		else
			return calY;
	}
	public double calZ()
	{
		if ( Double.isNaN( calZ ) )
		{
			final Object o = summaryMetadata_.get( "z-step_um" );

			if ( o == null )
				return 1.0;
			else
				return Double.parseDouble( o.toString() );
		}
		else
		{
			return calZ;
		}
	}
	public String calUnit() { return unit; }
	public int numTimepoints() { return Integer.parseInt( summaryMetadata_.get( "Frames" ).toString() ); }
	public int numPositions() { return Integer.parseInt( summaryMetadata_.get( "Positions" ).toString() ); }
	public int numChannelsAndAngles() { return Integer.parseInt( summaryMetadata_.get( "Channels" ).toString() ); }
	public int numChannels()
	{
		final int totalNum = numChannelsAndAngles();
		final int numAngles = numAngles();

		if ( totalNum % numAngles != 0 )
			throw new RuntimeException( "Channels & Angle number is not symmetric. This is not supported. TotalNumCh=" + totalNum + ", numAngles=" + numAngles );

		return totalNum / numAngles();
	}

	public int numAngles()
	{
		// MVRotations = 0_90_0_90
		// >>> channels increasing, angles increasing faster

		final String ac = summaryMetadata_.get( "MVRotations" ).toString().trim();
		final String[] entries = ac.split( "_" );

		final HashSet< Integer > uniqueAngles = new HashSet< Integer >();

		for ( int i = 0; i < entries.length; ++i )
			uniqueAngles.add( Integer.parseInt( entries[ i ] ) );

		return uniqueAngles.size();
	}

	public String rotationAngle( final int angleId )
	{
		if ( angleId < 0 || angleId >= numAngles() )
		{
			IOFunctions.println( "No angle with id " + angleId + ", there are only " + numAngles() + " angles." );
			return String.valueOf( angleId );
		}

		if ( this.angleNames != null )
			return this.angleNames.get( angleId );

		// MVRotations = 0_90_0_90
		// >>> channels increasing, angles increasing faster

		try
		{
			final String ac = summaryMetadata_.get( "MVRotations" ).toString().trim();
			final String[] entries = ac.split( "_" );
	
			return entries[ angleId ];
		}
		catch ( Exception e )
		{
			IOFunctions.println( "Failed to get rotation angle: " + e );
			return "0";
		}
	}

	public int interleavedId( final int channelId, final int angleId )
	{
		return channelId * numAngles() + angleId;
	}

	public String channelName( final int channelId )
	{
		if ( channelId < 0 || channelId >= numChannels() )
		{
			IOFunctions.println( "No channel with id " + channelId + ", there are only " + numChannels() + " channels." );
			return String.valueOf( channelId );
		}

		if ( this.channelNames != null )
			return this.channelNames.get( channelId );

		// ChNames: ["Camera_A-561","Camera_B-561","Camera_A-488","Camera_B-488"]

		try
		{
			String as = summaryMetadata_.get( "ChNames" ).toString().trim();
	
			if ( as.startsWith( "[" ) && as.endsWith( "]") )
				as = as.substring( 1, as.length() - 1 );
	
			final String[] entries = as.split( "," );
	
			if ( entries.length != numChannelsAndAngles() )
			{
				IOFunctions.println( "Number of entries in " + summaryMetadata_.get( "ChNames" ).toString().trim() + " does not match numAngles()*numChannels()=" + numChannelsAndAngles() );
				return String.valueOf( channelId );
			}

			String entry = entries[ interleavedId( channelId, 0 ) ];

			if ( entry.indexOf( '-' ) > 0 )
				return entry.substring( entry.indexOf( '-' )  + 1, entry.length() - 1 );
			else
				return entry.substring( 1, entry.length() - 1 );
		}
		catch ( Exception e )
		{
			IOFunctions.println( "Failed to parse channel name: " + e );
			return String.valueOf( channelId );
		}
	}

	public String rotationAxisName()
	{
		final double[] r = rotationAxis();

		if ( r[ 0 ] == 1 && r[ 1 ] == 0 && r[ 2 ] == 0 )
			return "X-Axis";
		else if ( r[ 0 ] == 0 && r[ 1 ] == 1 && r[ 2 ] == 0 )
			return "Y-Axis";
		else if ( r[ 0 ] == 0 && r[ 0 ] == 1 && r[ 1 ] == 0 )
			return "Z-Axis";
		else
			return "Rotation Axis Vector: " + Util.printCoordinates( r );
	}

	public int rotationAxisIndex()
	{
		final double[] r = rotationAxis();

		if ( r[ 0 ] == 1 && r[ 1 ] == 0 && r[ 2 ] == 0 )
			return 0;
		else if ( r[ 0 ] == 0 && r[ 1 ] == 1 && r[ 2 ] == 0 )
			return 1;
		else if ( r[ 0 ] == 0 && r[ 0 ] == 1 && r[ 1 ] == 0 )
			return 2;
		else
			return -1;
	}

	public void setRotAxis( final double[] axis ) { this.rotAxis = axis; }
	public double[] rotationAxis()
	{
		// MVRotationAxis = 0_1_0

		if ( this.rotAxis == null )
		{
			String as = summaryMetadata_.get( "MVRotationAxis" ).toString();
			String[] v = as.split( "_" );
			double x = Double.parseDouble( v[ 0 ].trim() );
			double y = Double.parseDouble( v[ 1 ].trim() );
			double z = Double.parseDouble( v[ 2 ].trim() );
	
			return new double[]{ x, y, z };
		}
		else
		{
			return rotAxis;
		}
	}

	final private static long unsignInt( final int i )
	{
		long val = Integer.MAX_VALUE & i;
		if (i < 0)
			val += BIGGEST_INT_BIT;

		return val;
	}

	public class IFDData
	{
		public long pixelOffset;
		public long bytesPerImage;
		public long mdOffset;
		public long mdLength;
		public long nextIFD;
		public long nextIFDOffsetLocation;
	}

	public class IFDEntry
	{
		final public char tag, type;
		final public long count, value;

		public IFDEntry( final char tg, final char typ, final long cnt, final long val )
		{
			this.tag = tg;
			this.type = typ;
			this.count = cnt;
			this.value = val;
		}
	}

	public static void main(String[] args) throws IOException
	{
		final File f;
		
		f = new File( "/Users/preibischs/Documents/Microscopy/SPIM/561D", "MMStack_Pos0.ome.tif" );
		//f = new File( "/Users/preibischs/Documents/Microscopy/SPIM/BeadVolume2ch_1", "MMStack_Pos0.ome.tif" );

		MultipageTiffReader r = new MultipageTiffReader( f );

		for ( final String k : r.getSummaryMetadata().keySet() )
			System.out.println( k + ": " + r.getSummaryMetadata().get( k ) );

		final String pixelType = r.getPixelType();

		if ( pixelType.toUpperCase().startsWith( "RGB" ) )
		{
			IOFunctions.println( "RGB not supported." );
			return;
		}

		System.out.println( "width: " + r.width() );
		System.out.println( "height: " + r.height() );
		System.out.println( "depth: " + r.depth() );
		System.out.println( "calX: " + r.calX() );
		System.out.println( "calY: " + r.calY() );
		System.out.println( "calZ: " + r.calZ() );
		System.out.println( "numAngles: " + r.numAngles() );
		System.out.println( "rotation axis: " + Util.printCoordinates( r.rotationAxis() ) );
		for ( int i = 0; i < r.numAngles(); ++i )
			System.out.println( "angle " + i + " rotation: "  + r.rotationAngle( i ) );
		
		System.out.println( "numChannels: " + r.numChannels() );
		for ( int i = 0; i < r.numChannels(); ++i )
			System.out.println( "channel " + i + " name: "  + r.channelName( i ) );
		
		System.out.println( "numTimepoints: " + r.numTimepoints() );
		System.out.println( "numPositions: " + r.numPositions() );

		/*
		ArrayList< String > indices = new ArrayList< String >();

		for ( final String k : r.getIndexKeys() )
			indices.add( k );

		Collections.sort( indices );
		for ( final String k : indices )
			System.out.println( k );
		*/
		
		final int c = 0;
		final int a = 1;
		final int s = 20;
		final int t = 0;
		final int p = 0;
		
		String label = generateLabel( r.interleavedId( c, a ), s, t, p );
		Object o = r.readImage( label ).getA();

		System.out.println( label );
		System.out.println( r.indexMap_.get( label ) );
		new ImageJ();
		if ( o instanceof byte[] )
		{
			//ImageJFunctions.show( ArrayImgs.unsignedBytes( (byte[])o, r.width(), r.height() ) );
		}
		else if ( o instanceof short[] )
		{
			System.out.println( ((short[])o).length );
			ImageJFunctions.show( ArrayImgs.unsignedShorts( (short[])o, r.width(), r.height() ) );
		}
	}
}
