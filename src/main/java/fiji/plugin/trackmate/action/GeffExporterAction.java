/*-
 * #%L
 * TrackMate: your buddy for everyday tracking.
 * %%
 * Copyright (C) 2025 TrackMate developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */
package fiji.plugin.trackmate.action;

import static fiji.plugin.trackmate.gui.Icons.TRACKMATE_ICON;

import java.awt.Component;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.HeadlessException;
import java.io.File;
import java.io.FilenameFilter;

import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.scijava.plugin.Plugin;

import fiji.plugin.trackmate.SelectionModel;
import fiji.plugin.trackmate.TrackMate;
import fiji.plugin.trackmate.gui.displaysettings.DisplaySettings;
import fiji.plugin.trackmate.io.IOUtils;
import fiji.plugin.trackmate.io.TrackMateGefffWriter;
import fiji.plugin.trackmate.util.TMUtils;
import ij.IJ;

public class GeffExporterAction extends AbstractTMAction
{

	public static final String NAME = "Export to GEFF";

	public static final String KEY = "GEFF_EXPORTER";

	public static final String INFO_TEXT = "<html>" +
			"Export the current TrackMate model to the GEFF format."
			+ "<p>"
			+ "GEFF is a generic file format for storing tracking data, "
			+ "and exchanging it between different tracking software."
			+ "See the <a url=\"https://live-image-tracking-tools.github.io/geff\">GEFF webpage</a> for details: https://live-image-tracking-tools.github.io/geff"
			+ "<p>"
			+ "The supported version of the GEFF format is " + TrackMateGefffWriter.GEFF_VERSION
			+ "</html>";

	public static final ImageIcon ICON = null;

	private static File file = new File( System.getProperty( "user.home" ), "tracks.zarr" );

	@Override
	public void execute( final TrackMate trackmate, final SelectionModel selectionModel, final DisplaySettings displaySettings, final Frame parent )
	{
		final File f1 = TMUtils.proposeTrackMateSaveFile( trackmate.getSettings(), logger );
		final File f2 = new File( f1.getAbsolutePath().replaceAll( "\\.xml$", ".zarr" ) );

		/*
		 * Should we update the static field file? only if the last part of the
		 * path has changed. This indicates that the user is running the action
		 * with a new model. Not perfectly accurate of course.
		 */
		if ( !file.getAbsolutePath().endsWith( f2.getName() ) )
			file = f2;

		final File f3 = askForFileForSaving( file, parent, "Save to a GEFF file", ".zarr", "GEFF file" );
		if ( f3 == null )
		{
			logger.log( "GEFF export canceled.\n" );
			return;
		}
		file = f3;

		logger.log( "Exporting to GEFF file: " + file.getAbsolutePath() + "\n" );
		try
		{
			TrackMateGefffWriter.export( trackmate.getModel(), file.getAbsolutePath() );
			logger.log( "Export completed.\n" );
		}
		catch ( final Exception e )
		{
			logger.error( "An error occurred while exporting to GEFF file: " + e.getMessage() + "\n" );
			e.printStackTrace();
			return;
		}
	}

	@Plugin( type = TrackMateActionFactory.class )
	public static class Factory implements TrackMateActionFactory
	{

		@Override
		public String getInfoText()
		{
			return INFO_TEXT;
		}

		@Override
		public String getKey()
		{
			return KEY;
		}

		@Override
		public TrackMateAction create()
		{
			return new GeffExporterAction();
		}

		@Override
		public ImageIcon getIcon()
		{
			return ICON;
		}

		@Override
		public String getName()
		{
			return NAME;
		}
	}

	/**
	 * TODO: move to {@link IOUtils}
	 *
	 * @param file
	 *            a file to start browsing from.
	 * @param parent
	 *            the parent component for the dialog, or null to use the
	 *            default dialog.
	 * @param title
	 *            the title of the dialog.
	 * @param extension
	 *            the file extension to filter by, including the dot, e.g.
	 *            ".xml"
	 * @param description
	 *            the description of the file type, e.g. "TrackMate XML file".
	 * @return
	 */
	public static File askForFileForSaving(
			final File file,
			final Frame parent,
			final String title,
			final String extension,
			final String description )
	{
		if ( IJ.isMacintosh() && parent != null )
		{
			// use the native file dialog on the mac
			final FileDialog dialog = new FileDialog( parent, title, FileDialog.SAVE );
			dialog.setIconImage( TRACKMATE_ICON.getImage() );
			dialog.setDirectory( file.getParent() );
			dialog.setFile( file.getName() );
			final FilenameFilter filter = ( dir, name ) -> name.endsWith( extension );
			dialog.setFilenameFilter( filter );
			dialog.setVisible( true );
			String selectedFile = dialog.getFile();
			if ( null == selectedFile )
				return null;
			if ( !selectedFile.endsWith( extension ) )
				selectedFile += extension;
			return new File( dialog.getDirectory(), selectedFile );
		}
		else
		{
			final JFileChooser fileChooser = new JFileChooser( file.getParent() )
			{
				private static final long serialVersionUID = 1L;

				@Override
				protected JDialog createDialog( final Component lParent ) throws HeadlessException
				{
					final JDialog dialog = super.createDialog( lParent );
					dialog.setIconImage( TRACKMATE_ICON.getImage() );
					return dialog;
				}
			};
			fileChooser.setSelectedFile( file );
			final FileNameExtensionFilter filter = new FileNameExtensionFilter( description, extension.replace( ".", "" ) );
			fileChooser.setFileFilter( filter );

			final int returnVal = fileChooser.showSaveDialog( parent );
			if ( returnVal == JFileChooser.APPROVE_OPTION )
				return fileChooser.getSelectedFile();

			return null;
		}
	}
}
