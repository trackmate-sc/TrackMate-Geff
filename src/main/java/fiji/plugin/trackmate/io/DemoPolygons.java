package fiji.plugin.trackmate.io;

import java.io.File;
import java.io.IOException;

import fiji.plugin.trackmate.Model;
import ucar.ma2.InvalidRangeException;

public class DemoPolygons
{
	public static void main( final String[] args ) throws IOException, InvalidRangeException
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
