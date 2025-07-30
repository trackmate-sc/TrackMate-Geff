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
package fiji.plugin.trackmate.io;

import java.io.File;
import java.io.IOException;

import fiji.plugin.trackmate.Model;
import fiji.plugin.trackmate.SelectionModel;
import fiji.plugin.trackmate.Settings;
import fiji.plugin.trackmate.TrackMate;
import fiji.plugin.trackmate.gui.displaysettings.DisplaySettings;
import fiji.plugin.trackmate.gui.wizard.TrackMateWizardSequence;
import fiji.plugin.trackmate.visualization.hyperstack.HyperStackDisplayer;
import ij.ImageJ;
import ij.ImagePlus;

public class DemoPolygons
{
	public static void main( final String[] args ) throws IOException
	{
		final String filename = "samples/MAX_Merged.xml";
		final String outputZarrPath = "samples/MAX_Merged.zarr";

		final TmXmlReader reader = new TmXmlReader( new File( filename ) );
		if ( !reader.isReadingOk() )
		{
			System.err.println( "Error reading file: " + reader.getErrorMessage() );
			return;
		}

		final Model model = reader.getModel();

		/*
		 * 1. Export to GEFF
		 */

		TrackMateGeffWriter.export( model, outputZarrPath );

		/*
		 * 2. Reload from GEFF
		 */

		final Model importedModel = TrackMateGeffReader.readModel( outputZarrPath );

		/*
		 * 3. Display in TrackMate
		 */

		final ImagePlus imp = reader.readImage();
		final Settings settings = reader.readSettings( imp );
		final DisplaySettings ds = reader.getDisplaySettings();
		final TrackMate trackmate = new TrackMate( importedModel, settings );

		ImageJ.main( args );
		final SelectionModel selectionModel = new SelectionModel( importedModel );

		new HyperStackDisplayer( model, selectionModel, imp, ds ).render();

		final TrackMateWizardSequence sequence = new TrackMateWizardSequence( trackmate, selectionModel, ds );
		sequence.setCurrent( "ConfigureViews" );
		sequence.run( "Imported from GEFF" ).setVisible( true );
	}
}
