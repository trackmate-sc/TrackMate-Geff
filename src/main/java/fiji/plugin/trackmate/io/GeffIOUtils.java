package fiji.plugin.trackmate.io;

import java.util.concurrent.atomic.AtomicBoolean;

import fiji.plugin.trackmate.Model;
import fiji.plugin.trackmate.TrackMate;
import fiji.plugin.trackmate.detection.DetectionUtils;
import ij.ImagePlus;

public class GeffIOUtils
{

	public static final boolean is2D( final Model model )
	{
		final AtomicBoolean is2dFlag = new AtomicBoolean( true );
		model.getSpots().iterable( true ).forEach( spot -> {
			final double z = spot.getDoublePosition( 2 );
			// check with tolerance
			if ( Math.abs( z ) > 1e-10 )
			{
				is2dFlag.set( false );
				return;
			}
		} );
		return is2dFlag.get();
	}

	public static final boolean is2D( final TrackMate trackmate )
	{
		final ImagePlus imp = trackmate.getSettings().imp;
		if ( null == imp )
			return is2D( trackmate.getModel() );
		else
			return DetectionUtils.is2D( imp );
	}
}
