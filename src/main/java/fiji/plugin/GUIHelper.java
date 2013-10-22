package fiji.plugin;

import ij.IJ;
import ij.gui.MultiLineLabel;
import ij.plugin.BrowserLauncher;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class GUIHelper
{
	public static final void addHyperLinkListener(final MultiLineLabel text, final String myURL)
	{
		if ( text != null && myURL != null )
		{
			text.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					try
					{
						BrowserLauncher.openURL(myURL);
					}
					catch (Exception ex)
					{
						IJ.error("" + ex);
					}
				}
	
				@Override
				public void mouseEntered(MouseEvent e)
				{
					text.setForeground(Color.BLUE);
					text.setCursor(new Cursor(Cursor.HAND_CURSOR));
				}
	
				@Override
				public void mouseExited(MouseEvent e)
				{
					text.setForeground(Color.BLACK);
					text.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
				}
			});
		}
	}
	
	/**
	 * Removes any of those characters from a String: (, ), [, ], {, }, <, >
	 * 
	 * @param entry input (with brackets)
	 * @return input, but without any brackets
	 */
	public static String removeBrackets( String entry )
	{
		return removeSequences( entry, new String[] { "(", ")", "{", "}", "[", "]", "<", ">" } );
	}
	
	public static String removeSequences( String entry, final String[] sequences )
	{
		while ( contains( entry, sequences ) )
		{
			for ( final String s : sequences )
			{
				final int index = entry.indexOf( s );

				if ( index == 0 )
					entry = entry.substring( s.length(), entry.length() );
				else if ( index == entry.length() - s.length() )
					entry = entry.substring( 0, entry.length() - s.length() );
				else if ( index > 0 )
					entry = entry.substring( 0, index ) + entry.substring( index + s.length(), entry.length() );
			}
		}

		return entry;		
	}
	
	public static boolean contains( final String entry, final String[] sequences )
	{
		for ( final String seq : sequences )
			if ( entry.contains( seq ) )
				return true;
		
		return false;
	}	
}
