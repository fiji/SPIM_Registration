//import ij.IJ;
//import ij.ImageJ;
//import ij.gui.GenericDialog;
//import ij.plugin.PlugIn;
//
///**
// * This is a minimal example for a macro-recordable plugin
// * that actually works in headless mode (because it does not
// * access or instantiate any text-related AWT components.
// */
//public class HongKee_Example implements PlugIn {
//	/**
//	 * This method gets called by ImageJ / Fiji.
//	 *
//	 * @param arg can be specified in plugins.config
//	 * @see ij.plugin.PlugIn#run(java.lang.String)
//	 */
//	@Override
//	public void run(String arg) {
//		final GenericDialog gd = new GenericDialog("Hello, HongKee!");
//		gd.addNumericField("radius", 5, 0);
//		gd.showDialog();
//		if (gd.wasCanceled()) return;
//		System.out.println("Now we have the radius: " + gd.getNextNumber());
//		IJ.log( "Now we have the radius: " + gd.getNextNumber() );
//	}
//
//	public static void main(String[] args)
//	{
//		ImageJ.main( args );
//		IJ.run("HongKee Example", "radius=10");
//		//new HongKee_Example().run(null);
//	}
//}