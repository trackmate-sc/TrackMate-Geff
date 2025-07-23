package fiji.plugin.trackmate.io;

import java.io.File;

import fiji.plugin.trackmate.Model;

public class DemoPolygons
{
	public static void main( final String[] args )
	{
		final String filename = "samples/ MAX_Merged.xml";
		final String outputZarrPath = "samples/MAX_Merged.zarr/tracks";

		final TmXmlReader reader = new TmXmlReader( new File( filename ) );
		if ( !reader.isReadingOk() )
		{
			System.err.println( "Error reading file: " + reader.getErrorMessage() );
			return;
		}

		final Model model = reader.getModel();
		TrackMateGefffWriter.export( model, outputZarrPath );
	}

}
