package spim.fiji.spimdata.explorer.popup;

import java.awt.Color;
import java.awt.Font;

import javax.swing.JLabel;

import spim.fiji.spimdata.explorer.ViewSetupExplorerPanel;

public class LabelPopUp extends JLabel implements ViewExplorerSetable
{
	private static final long serialVersionUID = 5234649267634013390L;

	public LabelPopUp( final String text )
	{
		super( text );
		this.setFont( new Font( Font.SANS_SERIF, Font.ITALIC, 11 ) );
		this.setForeground( new Color( 128, 128, 128 ));
	}

	@Override
	public JLabel setViewExplorer( final ViewSetupExplorerPanel< ?, ? > panel )
	{
		return this;
	}
}
