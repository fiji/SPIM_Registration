package ij2;

import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.io.File;

/**
 * Proof-of-concept code for ImageJ2 plugin
 */
@Plugin(type = Command.class, headless = true,
		menuPath = "Plugins>Multiview Reconstruction>Batch Processing>Define Multi-View Dataset")
public class DefineXmlCommand implements Command
{
	@Parameter(label = "type_of_dataset",
			choices = {
					"Image Stacks (LOCI Bioformats)",
					"Image Stacks (ImageJ Opener)",
					"MicroManager diSPIM Dataset",
					"Zeiss Lightsheet Z.1 Dataset (LOCI Bioformats)"})
	private String typeOfDataset;

	@Parameter(label = "xml_filename")
	private File xmlFile;

	@Parameter(label = "Results", type = ItemIO.OUTPUT)
	private String result;

	@Override
	public void run()
	{
		final StringBuilder sb = new StringBuilder();

		sb.append( "\n" );
		sb.append( typeOfDataset );
		sb.append( "\n" );
		sb.append( xmlFile );
		sb.append( "\n" );

		result =  sb.toString();
		System.out.println(result);
	}

	public static void main(final String... args) throws Exception {

		// Launch ImageJ as usual.
		final net.imagej.ImageJ ij = net.imagej.Main.launch(args);

		// Launch the "Widget Demo" command right away.
		ij.command().run(DefineXmlCommand.class, true);
	}
}