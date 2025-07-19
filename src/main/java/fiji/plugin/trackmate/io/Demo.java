package fiji.plugin.trackmate.io;

import java.io.File;

import fiji.plugin.trackmate.Model;

public class Demo
{

	public static void main( final String[] args )
	{
		final String tmFilename = "../TrackMate/samples/FakeTracks.xml";
		final String inputZarrPath = "samples/FakeTracks.zarr/tracks";

//		write( tmFilename, inputZarrPath );
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
