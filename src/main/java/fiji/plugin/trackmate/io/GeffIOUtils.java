package fiji.plugin.trackmate.io;

import java.util.concurrent.atomic.AtomicBoolean;

import fiji.plugin.trackmate.Model;
import fiji.plugin.trackmate.TrackMate;
import fiji.plugin.trackmate.detection.DetectionUtils;
import ij.ImagePlus;

public class GeffIOUtils
{

	/**
	 * Converts a unit string to the OME-Zarr format.
	 * <p>
	 * OME-Zarr spatial units:
	 *
	 * <pre>
	 * "angstrom", "attometer", "centimeter", "decimeter", "exameter", "femtometer",
	 * "foot", "gigameter", "hectometer", "inch", "kilometer", "megameter", "meter",
	 * "micrometer", "mile", "millimeter", "nanometer", "parsec", "petameter",
	 * "picometer", "terameter", "yard", "yoctometer", "yottameter", "zeptometer",
	 * "zettameter"
	 * </pre>
	 * <p>
	 * OME-Zarr time units:
	 *
	 * <pre>
	 * 'attosecond', 'centisecond', 'day', 'decisecond', 'exasecond', 'femtosecond',
	 * 'gigasecond', 'hectosecond', 'hour', 'kilosecond', 'megasecond', 'microsecond',
	 * 'millisecond', 'minute', 'nanosecond', 'petasecond', 'picosecond', 'second',
	 * 'terasecond', 'yoctosecond', 'yottasecond', 'zeptosecond', 'zettasecond'
	 * </pre>
	 *
	 * @param unit
	 *            the unit string to convert, e.g., "micrometer", "nm", "pixel",
	 *            etc.
	 * @return the corresponding OME-Zarr unit string if we know how to convert
	 *         the input.
	 */
	public static final String toOMEZarrUnits( final String unit )
	{
		if ( unit.toLowerCase().startsWith( "micro" ) || unit.toLowerCase().startsWith( "µm" ) )
			return "micrometer";

		if ( unit.toLowerCase().startsWith( "nano" ) || unit.toLowerCase().startsWith( "nm" ) )
			return "nanometer";

		if ( unit.toLowerCase().startsWith( "min" ) )
			return "minute";

		if ( unit.toLowerCase().startsWith( "sec" ) )
			return "second";

		if ( unit.toLowerCase().startsWith( "ms" ) )
			return "millisecond";

		return unit.toLowerCase();
	}

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
