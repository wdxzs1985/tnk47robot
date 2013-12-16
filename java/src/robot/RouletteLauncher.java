package robot;

public class RouletteLauncher {

	public static void main(final String[] args) {
		final String setup = args.length > 0 ? args[0] : "setup.txt";
		final MainLauncher launcher = new MainLauncher();
		launcher.init(setup);
		launcher.runRouletteRobot();
	}

}