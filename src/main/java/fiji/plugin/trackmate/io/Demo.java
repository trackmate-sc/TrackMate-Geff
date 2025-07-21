package fiji.plugin.trackmate.io;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;

import fiji.plugin.trackmate.Model;

public class Demo
{

	public static void main( final String[] args ) throws IOException
	{
		final String tmFilename = "../TrackMate/samples/FakeTracks.xml";
		final String inputZarrPath = "samples/FakeTracks.zarr/tracks";

		// Recursively delete the output Zarr path if it exists.
		FileUtils.deleteDirectory( new File( inputZarrPath ) );

		write( tmFilename, inputZarrPath );
		final Model model = TrackMateGeffIO.readFromGeff( inputZarrPath );
		System.out.println( "\n\n------------------" );
		System.out.println( model );
	}

	public static void write( final String filename, final String outputZarrPath )
	{
		final TmXmlReader reader = new TmXmlReader( new File( filename ) );
		if ( !reader.isReadingOk() )
		{
			System.err.println( "Error reading file: " + reader.getErrorMessage() );
			return;
		}

		final Model model = reader.getModel();
		TrackMateGeffIO.serializeToGeff( model, outputZarrPath );
	}
}
