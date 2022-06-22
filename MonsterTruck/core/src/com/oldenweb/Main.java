package com.oldenweb;

import com.badlogic.gdx.Application.ApplicationType;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.ParticleEffect;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef.BodyType;
import com.badlogic.gdx.physics.box2d.Box2DDebugRenderer;
import com.badlogic.gdx.physics.box2d.Contact;
import com.badlogic.gdx.physics.box2d.ContactImpulse;
import com.badlogic.gdx.physics.box2d.ContactListener;
import com.badlogic.gdx.physics.box2d.Joint;
import com.badlogic.gdx.physics.box2d.Manifold;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.physics.box2d.joints.WheelJointDef;
import com.badlogic.gdx.scenes.scene2d.Action;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.SnapshotArray;
import com.badlogic.gdx.utils.Timer;
import com.badlogic.gdx.utils.Timer.Task;
import com.badlogic.gdx.utils.viewport.FillViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

// Class
public class Main extends ApplicationAdapter {
	// const
	static final float SCREEN_WIDTH = 1920; // default screen width
	static final float SCREEN_HEIGHT = 1440; // default screen height
	static final float PPM = 32; // pixels per meter in Box2D world
	final boolean SHOW_DEBUG = false; // show debug
	final float CAMERA_OFFSET_X = 400; // camera offset left
	final float CAMERA_OFFSET_Y = 0; // camera offset top
	final float BRIGHTNESS_PRESSED = 0.9f; // button brightness when pressed
	final float MUSIC_VOLUME = 0.2f; // background music volume
	final float ACCELERATION = 5000; // hero acceleration
	final float MAX_SPEED = 15; // hero max speed
	final int MAP_WIDTH = 4; // number of map pieces to make a level
	final int BG_WIDTH = 7100; // background width for parallax effect

	// vars
	static Stage stage;
	static World world;
	static AssetManager assetManager;
	static InputListener controlListener;
	static float ratio;
	static Array<Body> destroyBodies;
	static Array<Joint> destroyJoints;
	OrthographicCamera cam;
	JsonValue map;
	float mapWidth;
	float mapHeight;
	float stageXmin;
	float stageXmax;
	float stageYmin;
	float stageYmax;
	boolean keyLeft;
	boolean keyRight;
	boolean isSigned;
	Preferences pref;
	Box2DDebugRenderer debug;
	String screenColor;
	SpriteBatch batch;
	int currentWidth;
	int currentHeight;
	Viewport viewport;
	InterfaceListener nativePlatform;
	Music sndBg;
	boolean isPaused;
	Act controlLeft;
	Act controlRight;
	Act btnSign;
	Act btnSound;
	Act btnPause;
	Act hero;
	Act tireRear;
	Act tireFront;
	Group groupPause;
	int score = 0;
	Vector2 point;
	float currentVolume = 0;
	boolean isForeground = true;
	boolean assetsLoaded;
	String nextScreen;
	String screen = ""; // screen

	Group groupGameOver;
	Group groupBg;
	Group groupSky;
	Group groupClouds;
	TextureAtlas numbers;
	Array<Act> clouds;
	Sound sndDrive;
	Sound sndMotor;
	Sound sndSpring;
	Act btnRestart;
	ParticleEffect effect;

	// Constructor
	public Main(InterfaceListener nativePlatform) {
		this.nativePlatform = nativePlatform;
	}

	@Override
	public void create() {
		batch = new SpriteBatch();
		controlListener = new CONTROL();
		destroyBodies = new Array<Body>();
		destroyJoints = new Array<Joint>();
		currentWidth = Gdx.graphics.getWidth();
		currentHeight = Gdx.graphics.getHeight();
		assetManager = new AssetManager();
		Gdx.input.setCatchBackKey(true); // prevent back on mobile
		clouds = new Array<Act>();

		// debug
		if (SHOW_DEBUG)
			debug = new Box2DDebugRenderer();

		// preferences
		pref = Gdx.app.getPreferences("preferences");

		// send score
		if (pref.contains("score")) {
			score = pref.getInteger("score");
			nativePlatform.saveScore(score);
		}

		// camera & viewport
		cam = new OrthographicCamera(SCREEN_WIDTH / PPM, SCREEN_HEIGHT / PPM);
		viewport = new FillViewport(SCREEN_WIDTH, SCREEN_HEIGHT);

		// world
		world = new World(new Vector2(0, -10), true);
		world.setContactListener(new CONTACT());

		// stage
		stage = new Stage(viewport, batch);
		Gdx.input.setInputProcessor(stage);

		// load assets
		Lib.loadAssets(false);

		// loading
		stage.addActor(new Act("", (SCREEN_WIDTH - assetManager.get("loading.png", Texture.class).getWidth()) / 2,
				(SCREEN_HEIGHT - assetManager.get("loading.png", Texture.class).getHeight()) / 2, new TextureRegion(assetManager
						.get("loading.png", Texture.class))));

		// effect
		effect = new ParticleEffect();
		effect.load(Gdx.files.internal("effect"), Gdx.files.internal(""));
	}

	// onAssetsLoaded
	void onAssetsLoaded() {
		assetsLoaded = true;
		Lib.texturesFilter(); // textures smoothing

		// bg music
		sndBg = assetManager.get("sndBg.ogg", Music.class);
		bgSound();

		// sound drive & motor
		sndDrive = assetManager.get("sndDrive.ogg", Sound.class);
		sndMotor = assetManager.get("sndMotor.ogg", Sound.class);
		sndSpring = assetManager.get("sndSpring.ogg", Sound.class);

		// numbers
		numbers = assetManager.get("number.atlas", TextureAtlas.class);

		loadScreen("main");
	}

	// loadScreen
	void loadScreen(String screen) {
		clearScreen();
		nextScreen = screen;
		Timer.schedule(SHOW_SCREEN, Math.min(Gdx.graphics.getDeltaTime(), 0.02f));
	}

	// SHOW_SCREEN
	Task SHOW_SCREEN = new Task() {
		@Override
		public void run() {
			screen = nextScreen;

			if (screen.equals("main")) { // MAIN
				// load screen
				map = new JsonReader().parse(Gdx.files.internal("main.hmp"));
				mapWidth = map.getInt("map_width", 0);

				// sky
				groupSky = Lib.addGroup("sky", map, stage.getRoot(), 0);

				// clouds
				groupClouds = Lib.addGroup("cloud", map, stage.getRoot(), 0);
				SnapshotArray<Actor> group = groupClouds.getChildren();
				for (int i = 0; i < group.size; i++) {
					clouds.add((Act) group.get(i));
					clouds.get(i).cloudSpeed = (float) (-0.2f - Math.random() * 0.9f);
				}

				// layers
				Lib.addGroup("bg", map, stage.getRoot(), 0);
				Lib.addGroup("ground", map, stage.getRoot(), 0);
				Lib.addGroup("platform", map, stage.getRoot(), 0);

				// hero
				addHero();

				// menu buttons array
				Array<Act> buttons = new Array<Act>();

				// btnStart
				buttons.add(Lib.addLayer("btnStart", map, stage.getRoot(), 0).first());

				// sign button
				btnSign = Lib.addLayer("btnSign", map, stage.getRoot(), 0).first();
				buttons.add(btnSign);
				setSigned(isSigned);

				// btnLeaders
				buttons.add(Lib.addLayer("btnLeaders", map, stage.getRoot(), 0).first());

				// sound buttons
				btnSound = Lib.addLayer("btnSound", map, stage.getRoot(), 0).first();
				btnSound.tex = new TextureRegion(assetManager.get(
						pref.getBoolean("mute", false) ? "btnSound.png" : "btnMute.png", Texture.class));
				buttons.add(btnSound);

				// btnQuit
				buttons.add(Lib.addLayer("btnQuit", map, stage.getRoot(), 0).first());

				// buttons animation
				Vector2 point = stage.screenToStageCoordinates(new Vector2(Gdx.graphics.getWidth() / 2,
						Gdx.graphics.getHeight() / 2));
				float animSpeed = 0.3f; // animation speed
				for (int i = 0; i < buttons.size; i++) {
					buttons.get(i).setAlpha(0);
					buttons.get(i).setRotation((float) (Math.random() * 360));
					buttons.get(i).setScale(0.5f);
					buttons.get(i).addAction(
							Actions.sequence(Actions.moveTo(point.x - buttons.get(i).getWidth() / 2, point.y
									- buttons.get(i).getHeight() / 2), Actions.delay(i * animSpeed * 0.5f), Actions.parallel(
									Actions.alpha(1, animSpeed), Actions.rotateTo(0, animSpeed),
									Actions.scaleTo(1, 1, animSpeed), Actions.moveTo(buttons.get(i).getX(),
											buttons.get(i).getY(), animSpeed, Interpolation.swingOut))));
				}

				// drive sound
				driveSound(true);
			} else if (screen.equals("game")) { // GAME
				// load level map
				map = new JsonReader().parse(Gdx.files.internal("level.hmp"));

				// mapWidth
				int mapPieceWidth = map.getInt("map_width", 0);
				mapWidth = mapPieceWidth * MAP_WIDTH;

				// sky
				groupSky = Lib.addGroup("sky" + Math.round(Math.random() * 3), map, stage.getRoot(), 0);

				// clouds
				groupClouds = Lib.addGroup("cloud", map, stage.getRoot(), 0);
				SnapshotArray<Actor> group = groupClouds.getChildren();
				for (int i = 0; i < group.size; i++) {
					clouds.add((Act) group.get(i));
					clouds.get(i).cloudSpeed = (float) (-0.2f - Math.random() * 0.9f);
				}

				// bg
				groupBg = Lib.addGroup("bg" + Math.round(Math.random() * 7), map, stage.getRoot(), 0);

				// ground
				int rand = (int) Math.round(Math.random() * 25); // random
				for (int i = 0; i < MAP_WIDTH; i++)
					Lib.addLayer("g" + rand, map, stage.getRoot(), mapPieceWidth * i);

				// start
				Lib.addLayer("start", map, stage.getRoot(), 0);

				// finish
				Lib.addLayer("finish", map, stage.getRoot(), mapWidth);

				// platform
				for (int j = 0; j < MAP_WIDTH; j++) {
					Array<Act> actors = Lib.addLayer("p" + Math.round(Math.random() * 40), map, stage.getRoot(), mapPieceWidth
							* j);
					for (int i = 0; i < actors.size; i++)
						if (actors.get(i).getName().equals("car") || actors.get(i).getName().equals("bridge"))
							makeAnchors(actors.get(i));
				}

				// hero
				addHero();

				// load game map
				map = new JsonReader().parse(Gdx.files.internal("game.hmp"));

				// controls
				controlLeft = Lib.addLayer("controlLeft", map, stage.getRoot(), 0).first();
				controlRight = Lib.addLayer("controlRight", map, stage.getRoot(), 0).first();
				controlLeft.addAction(Actions.sequence(Actions.delay(3), Actions.alpha(0, 1)));
				controlRight.addAction(Actions.sequence(Actions.delay(3), Actions.alpha(0, 1)));

				// btnPause
				btnPause = Lib.addLayer("btnPause", map, stage.getRoot(), 0).first();

				// btnRestart
				btnRestart = Lib.addLayer("btnRestart", map, stage.getRoot(), 0).first();

				// groupGameOver
				groupGameOver = Lib.addGroup("groupGameOver", map, stage.getRoot(), 0);
				groupGameOver.setVisible(false);

				// groupPause
				groupPause = Lib.addGroup("groupPause", map, stage.getRoot(), 0);
				groupPause.setVisible(false);
				btnSound = groupPause.findActor("btnSound");
				btnSound.tex = new TextureRegion(assetManager.get(
						pref.getBoolean("mute", false) ? "btnSound.png" : "btnMute.png", Texture.class));

				// drive sound
				driveSound(true);
			}

			// map config
			mapHeight = map.getInt("map_height", 0);
			screenColor = map.getString("map_color", null);

			// stage keyboard focus
			Act a = new Act("");
			stage.addActor(a);
			a.addListener(controlListener);
			stage.setKeyboardFocus(a);

			// set stage moving XY limit
			stageLimit();
		}
	};

	// clearScreen
	void clearScreen() {
		screen = "";
		SHOW_SCREEN.cancel();
		keyLeft = false;
		keyRight = false;
		screenColor = null;
		isPaused = false;
		hero = null;
		clouds.clear();
		effect.reset();

		// sndDrive
		if (sndDrive != null)
			sndDrive.stop();

		// sndMotor
		if (sndMotor != null)
			sndMotor.stop();

		// sndSpring
		if (sndSpring != null)
			sndSpring.stop();

		// clear world
		world.clearForces();
		world.getJoints(destroyJoints);
		world.getBodies(destroyBodies);

		// clear stage
		stage.clear();
		render();

		// loading
		stage.addActor(new Act("", (SCREEN_WIDTH - assetManager.get("loading.png", Texture.class).getWidth()) / 2,
				(SCREEN_HEIGHT - assetManager.get("loading.png", Texture.class).getHeight()) / 2, new TextureRegion(assetManager
						.get("loading.png", Texture.class))));
	}

	@Override
	public void render() {
		// bg music volume
		if (!screen.isEmpty() && !pref.getBoolean("mute", false) && isForeground && currentVolume < MUSIC_VOLUME) {
			currentVolume += 0.001f;
			sndBg.setVolume(currentVolume);
		}

		// clear screen
		if (screenColor != null) {
			Color color = Color.valueOf(screenColor);
			Gdx.gl.glClearColor(color.r, color.g, color.b, 1);
		}
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

		// screen render
		if (screen.equals("game"))
			renderGame();
		else if (screen.equals("main"))
			renderMain();
		else {
			// camera position
			stage.getRoot().setPosition(0, 0);
			cam.position.set((SCREEN_WIDTH * 0.5f - stage.getRoot().getX()) / PPM,
					(SCREEN_HEIGHT * 0.5f - stage.getRoot().getY()) / PPM, 0);
			cam.update();

			// stage render
			stage.act(Math.min(Gdx.graphics.getDeltaTime(), 0.02f));
			stage.draw();

			// loading
			if (!assetsLoaded && assetManager.update())
				onAssetsLoaded();
		}

		// destroy world bodies
		if (!world.isLocked()) {
			for (int i = 0; i < destroyJoints.size; i++)
				world.destroyJoint(destroyJoints.get(i));
			for (int i = 0; i < destroyBodies.size; i++)
				world.destroyBody(destroyBodies.get(i));
			destroyJoints.clear();
			destroyBodies.clear();
		}

		// debug render
		if (SHOW_DEBUG)
			debug.render(world, cam.combined);
	}

	// renderMain
	void renderMain() {
		if (hero.enabled) {
			// move hero forward
			if (hero.body.getLinearVelocity().x < 15) {
				tireFront.body.applyTorque(-5000, true);
				tireRear.body.applyTorque(-3200, true);
			} else
				hero.body.setLinearVelocity(15, hero.body.getLinearVelocity().y);

			// add new hero
			if (hero.body.getPosition().x > 2200 / PPM) {
				hero.enabled = false;
				destroyJoints.add(hero.body.getJointList().get(0).joint);
				destroyJoints.add(hero.body.getJointList().get(1).joint);
				destroyBodies.add(hero.body);
				destroyBodies.add(tireFront.body);
				destroyBodies.add(tireRear.body);
				hero.remove();
				tireFront.remove();
				tireRear.remove();
				addHero();
			}
		}

		// world render
		world.step(1 / 30f, 8, 3);

		// camera position to center
		stage.getRoot().setPosition((SCREEN_WIDTH - mapWidth) * 0.5f, (SCREEN_HEIGHT - mapHeight) * 0.5f);
		cam.position.set((SCREEN_WIDTH * 0.5f - stage.getRoot().getX()) / PPM, (SCREEN_HEIGHT * 0.5f - stage.getRoot().getY())
				/ PPM, 0);
		cam.update();

		// clouds
		groupClouds.setX(-stage.getRoot().getX());
		for (int i = 0; i < clouds.size; i++) {
			clouds.get(i).moveBy(clouds.get(i).cloudSpeed, 0);
			if (clouds.get(i).getX() < -clouds.get(i).getWidth()) {
				clouds.get(i).setX(SCREEN_WIDTH);
				clouds.get(i).cloudSpeed = (float) (-0.2f - Math.random() * 0.9f);
			}
		}

		// stage render
		stage.act(Math.min(Gdx.graphics.getDeltaTime(), 0.02f));
		stage.draw();
	}

	// renderGame
	void renderGame() {
		if (!isPaused) {
			if (keyLeft || keyRight) {
				if (keyLeft) {
					// move back
					if (hero.body.getLinearVelocity().x > -MAX_SPEED) {
						tireFront.body.applyTorque(ACCELERATION, true);
						tireRear.body.applyTorque(ACCELERATION, true);
					} else
						hero.body.setLinearVelocity(-MAX_SPEED, hero.body.getLinearVelocity().y);
				} else if (keyRight) {
					// move forward
					if (hero.body.getLinearVelocity().x < MAX_SPEED) {
						tireFront.body.applyTorque(-ACCELERATION, true);
						tireRear.body.applyTorque(-ACCELERATION, true);
					} else
						hero.body.setLinearVelocity(MAX_SPEED, hero.body.getLinearVelocity().y);
				}
			} else {
				// break
				tireFront.body.setAngularVelocity(tireFront.body.getAngularVelocity() * 0.9f);
				tireRear.body.setAngularVelocity(tireRear.body.getAngularVelocity() * 0.9f);
			}

			// camera position to hero
			stage.getRoot().setX(MathUtils.clamp(SCREEN_WIDTH * 0.5f - CAMERA_OFFSET_X - hero.getX(), stageXmin, stageXmax));
			stage.getRoot().setY(MathUtils.clamp(SCREEN_HEIGHT * 0.5f - CAMERA_OFFSET_Y - hero.getY(), stageYmin, stageYmax));
			cam.position.set((SCREEN_WIDTH * 0.5f - stage.getRoot().getX()) / PPM,
					(SCREEN_HEIGHT * 0.5f - stage.getRoot().getY()) / PPM, 0);
			cam.update();

			// clouds
			groupClouds.setX(-stage.getRoot().getX());
			groupClouds.setY(Math.min(0, (SCREEN_HEIGHT / 2 - CAMERA_OFFSET_Y - hero.getY()) * 0.3f));
			for (int i = 0; i < clouds.size; i++) {
				clouds.get(i).moveBy(clouds.get(i).cloudSpeed, 0);
				if (clouds.get(i).getX() < -clouds.get(i).getWidth()) {
					clouds.get(i).setX(SCREEN_WIDTH);
					clouds.get(i).cloudSpeed = (float) (-0.2f - Math.random() * 0.9f);
				}
			}

			// groupSky
			groupSky.setX(-stage.getRoot().getX());

			// groupBg
			groupBg.setX(stage.getRoot().getX() / stageXmin * (mapWidth - BG_WIDTH));
			groupBg.setY(Math.min(0, (SCREEN_HEIGHT / 2 - CAMERA_OFFSET_Y - hero.getY()) * 0.5f));

			// groups
			groupPause.setPosition(-stage.getRoot().getX(), -stage.getRoot().getY());
			groupGameOver.setPosition(-stage.getRoot().getX(), -stage.getRoot().getY());

			// controlLeft
			point = stage.screenToStageCoordinates(new Vector2(0, Gdx.graphics.getHeight()));
			controlLeft.setPosition(point.x - stage.getRoot().getX(), point.y - stage.getRoot().getY());

			// controlRight
			point = stage.screenToStageCoordinates(new Vector2(Gdx.graphics.getWidth(), Gdx.graphics.getHeight()));
			controlRight
					.setPosition(point.x - controlRight.getWidth() - stage.getRoot().getX(), point.y - stage.getRoot().getY());

			// btnPause
			point = stage.screenToStageCoordinates(new Vector2(Gdx.graphics.getWidth(), 0));
			btnPause.setPosition(point.x - btnPause.getWidth() - 20 - stage.getRoot().getX(), point.y - btnPause.getHeight() - 20
					- stage.getRoot().getY());

			// btnRestart
			btnRestart.setPosition(btnPause.getX() - btnRestart.getWidth() - 20, btnPause.getY());

			// render
			world.step(1 / 30f, 8, 3);
			stage.act(Math.min(Gdx.graphics.getDeltaTime(), 0.02f));
		}

		stage.draw();
	}

	@Override
	public void pause() {
		if (!screen.isEmpty()) {
			sndBg.pause();
			isForeground = false;
			driveSound(false);
			motorSound(false);
		}

		super.pause();
	}

	@Override
	public void resume() {
		super.resume();
		if (!screen.isEmpty()) {
			isForeground = true;

			// finish load assets
			if (!assetManager.update())
				assetManager.finishLoading();

			bgSound();
			driveSound(true);
		}
	}

	@Override
	public void resize(int width, int height) {
		super.resize(width, height);
		viewport.update(width, height);
		ratio = Math.max((float) viewport.getScreenWidth() / SCREEN_WIDTH, (float) viewport.getScreenHeight() / SCREEN_HEIGHT);

		if (!Gdx.graphics.isFullscreen()) {
			currentWidth = width;
			currentHeight = height;
		}

		stageLimit();
	}

	@Override
	public void dispose() {
		clearScreen();
		batch.dispose();
		stage.dispose();
		assetManager.clear();
		effect.dispose();

		if (debug != null)
			debug.dispose();

		if (world != null)
			world.dispose();

		System.gc();
	}

	// setSigned
	public void setSigned(boolean signed) {
		isSigned = signed;
		btnSign.tex = new TextureRegion(assetManager.get(signed ? "btnSignOut.png" : "btnSignIn.png", Texture.class));
	}

	// saveScore
	public boolean saveScore(int score) {
		if (!pref.contains("score") || score > pref.getInteger("score")) {
			this.score = score;
			pref.putInteger("score", score);
			pref.flush();
			return true;
		}

		return false;
	}

	// stageLimit
	void stageLimit() {
		stageXmin = SCREEN_WIDTH - mapWidth + viewport.getLeftGutterWidth() / ratio;
		stageXmax = -viewport.getLeftGutterWidth() / ratio;
		stageYmin = SCREEN_HEIGHT - mapHeight + viewport.getTopGutterHeight() / ratio;
		stageYmax = -viewport.getTopGutterHeight() / ratio;
	}

	// bgSound
	void bgSound() {
		if (!pref.getBoolean("mute", false) && isForeground) {
			sndBg.setVolume(currentVolume);
			sndBg.setLooping(true);
			sndBg.play();
		}
	}

	// log
	void log(Object obj) {
		if (Gdx.app.getType().equals(ApplicationType.Desktop))
			System.out.println(obj);
		else
			Gdx.app.log("@", obj.toString());
	}

	// gameOver
	void gameOver() {
		// btnPause
		btnPause.enabled = false;
		btnPause.removeListener(controlListener);
		btnPause.addAction(Actions.alpha(0, 0.2f));

		// btnRestart
		btnRestart.enabled = false;
		btnRestart.removeListener(controlListener);
		btnRestart.addAction(Actions.alpha(0, 0.2f));

		// controls
		controlLeft.enabled = false;
		controlRight.enabled = false;
		controlLeft.removeListener(controlListener);
		controlRight.removeListener(controlListener);

		hero.enabled = false;
		keyLeft = false;
		keyRight = true;
		driveSound(false);
		motorSound(false);

		// sound
		if (!pref.getBoolean("mute", false) && isForeground)
			assetManager.get("sndCompleted.ogg", Sound.class).play(0.9f);

		showGroup(groupGameOver);
	}

	// showGroup
	void showGroup(Group group) {
		float delay = 0; // delay before show group

		// add score numbers
		String str = String.valueOf(score);
		Array<Act> actors = new Array<Act>();
		int numbersWidth = 0;
		for (int i = 0; i < str.length(); i++) {
			Act actor = new Act("", 0, 700, numbers.findRegion(str.substring(i, i + 1)));
			actors.add(actor);
			group.addActor(actor);
			numbersWidth += actor.getWidth();
		}

		// set numbers position
		float x_pos = (SCREEN_WIDTH - numbersWidth) / 2;
		for (int i = 0; i < actors.size; i++) {
			actors.get(i).setX(x_pos);
			x_pos += actors.get(i).getWidth();
		}

		// show
		group.setVisible(true);
		SnapshotArray<Actor> groupActors = group.getChildren();
		groupActors.get(0).addAction(Actions.sequence(Actions.alpha(0, 0), Actions.delay(delay), Actions.alpha(0.5f, 1)));
		for (int i = 1; i < groupActors.size; i++) {
			groupActors.get(i).setScale(0, 0);
            if(i != groupActors.size - 1)
			    groupActors.get(i).addAction(
					Actions.sequence(Actions.delay(delay + (i - 1) * 0.2f), Actions.scaleBy(1, 1, 1, Interpolation.elasticOut)));
            else
                groupActors.get(i).addAction(
                        Actions.sequence(Actions.delay(delay + (i - 1) * 0.2f), Actions.scaleBy(1, 1, 1, Interpolation.elasticOut), new Action() {
                            @Override
                            public boolean act(float delta) {
                                // show AdMob Interstitial
                                nativePlatform.admobInterstitial();
                                return true;
                            }
                        }));
		}
	}

	// CONTACT
	class CONTACT implements ContactListener {
		@Override
		public void beginContact(Contact contact) {
			Act actor1 = (Act) contact.getFixtureA().getBody().getUserData();
			Act actor2 = (Act) contact.getFixtureB().getBody().getUserData();
			Act otherActor;

			if (hero.enabled) {
				// hero and finish
				if ((actor1.getName().equals("hero") && actor2.getName().equals("finish"))
						|| (actor2.getName().equals("hero") && actor1.getName().equals("finish"))) {
					score++;

					// save score
					if (saveScore(score))
						nativePlatform.saveScore(score);

					gameOver();
					return;
				}

				// hero and car
				if ((actor1.getName().equals("hero") && actor2.getName().equals("car"))
						|| (actor2.getName().equals("hero") && actor1.getName().equals("car"))) {
					otherActor = actor1.getName().equals("hero") ? actor2 : actor1;
					if (otherActor.enabled) {
						// disable
						otherActor.enabled = false;
						otherActor.clearActions();
						otherActor.addAction(Actions.sequence(Actions.delay(0.5f), new Action() {
							@Override
							public boolean act(float delta) {
								// enable
								((Act) getActor()).enabled = true;
								return true;
							}
						}));

						// sound
						if (!pref.getBoolean("mute", false) && isForeground)
							assetManager.get("sndCar" + (int) Math.round(Math.random() * 6) + ".ogg", Sound.class).play(0.3f);
					}
					return;
				}

				// hero and container
				if ((actor1.getName().equals("hero") && actor2.getName().equals("container"))
						|| (actor2.getName().equals("hero") && actor1.getName().equals("container"))) {
					otherActor = actor1.getName().equals("hero") ? actor2 : actor1;
					if (otherActor.enabled) {
						// disable
						otherActor.enabled = false;
						otherActor.clearActions();
						otherActor.addAction(Actions.sequence(Actions.delay(1f), new Action() {
							@Override
							public boolean act(float delta) {
								// enable
								((Act) getActor()).enabled = true;
								return true;
							}
						}));

						// sound
						if (!pref.getBoolean("mute", false) && isForeground)
							assetManager.get("sndContainer.ogg", Sound.class).play(0.9f);
					}
					return;
				}

				// hero and stone
				if ((actor1.getName().equals("hero") && actor2.getName().equals("stone"))
						|| (actor2.getName().equals("hero") && actor1.getName().equals("stone"))) {
					otherActor = actor1.getName().equals("hero") ? actor2 : actor1;
					if (otherActor.enabled) {
						// disable
						otherActor.enabled = false;
						otherActor.clearActions();
						otherActor.addAction(Actions.sequence(Actions.delay(1f), new Action() {
							@Override
							public boolean act(float delta) {
								// enable
								((Act) getActor()).enabled = true;
								return true;
							}
						}));

						// sound
						if (!pref.getBoolean("mute", false) && isForeground)
							assetManager.get("sndStone" + (int) Math.round(Math.random() * 1) + ".ogg", Sound.class).play(0.9f);
					}
					return;
				}

				// hero and bulk
				if ((actor1.getName().equals("hero") && actor2.getName().equals("bulk"))
						|| (actor2.getName().equals("hero") && actor1.getName().equals("bulk"))) {
					otherActor = actor1.getName().equals("hero") ? actor2 : actor1;
					if (otherActor.enabled) {
						// disable
						otherActor.enabled = false;
						otherActor.clearActions();
						otherActor.addAction(Actions.sequence(Actions.delay(0.5f), new Action() {
							@Override
							public boolean act(float delta) {
								// enable
								((Act) getActor()).enabled = true;
								return true;
							}
						}));

						// sound
						if (!pref.getBoolean("mute", false) && isForeground)
							assetManager.get("sndBulk" + (int) Math.round(Math.random() * 3) + ".ogg", Sound.class).play(0.5f);
					}
					return;
				}

				// hero and box
				if ((actor1.getName().equals("hero") && actor2.getName().equals("boxSmall"))
						|| (actor2.getName().equals("hero") && actor1.getName().equals("boxSmall"))
						|| (actor1.getName().equals("hero") && actor2.getName().equals("boxBig"))
						|| (actor2.getName().equals("hero") && actor1.getName().equals("boxBig"))) {
					otherActor = actor1.getName().equals("hero") ? actor2 : actor1;
					if (otherActor.enabled) {
						// disable
						otherActor.enabled = false;
						otherActor.clearActions();
						otherActor.addAction(Actions.sequence(Actions.delay(0.5f), new Action() {
							@Override
							public boolean act(float delta) {
								// enable
								((Act) getActor()).enabled = true;
								return true;
							}
						}));

						// sound
						if (!pref.getBoolean("mute", false) && isForeground)
							assetManager.get("sndBox.ogg", Sound.class).play(0.4f);
					}
					return;
				}

				// hero spring
				if (actor1.getName().equals("hero") || actor2.getName().equals("hero")) {
					if (hero.body.getLinearVelocity().y < -6 && !pref.getBoolean("mute", false) && isForeground) {
						sndSpring.stop();
						sndSpring.play(0.5f);
					}
					return;
				}
			}
		}

		@Override
		public void endContact(Contact contact) {
		}

		@Override
		public void preSolve(Contact contact, Manifold oldManifold) {
		}

		@Override
		public void postSolve(Contact contact, ContactImpulse impulse) {
		}
	}

	// CONTROL
	class CONTROL extends InputListener {
		@Override
		public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
			if (((Act) event.getTarget()).enabled) {
				// each button
				if (event.getTarget().getName().substring(0, Math.min(3, event.getTarget().getName().length())).equals("btn")) {
					((Act) event.getTarget()).brightness = BRIGHTNESS_PRESSED;

					// sound
					if (!pref.getBoolean("mute", false) && isForeground)
						assetManager.get("sndBtn.ogg", Sound.class).play(0.9f);
				}

				if (screen.equals("game") && !isPaused && hero.enabled) {
					// controlLeft
					if (event.getTarget().getName().equals("controlLeft")) {
						keyLeft = true;
						motorSound(true);
						return true;
					}

					// controlRight
					if (event.getTarget().getName().equals("controlRight")) {
						keyRight = true;
						motorSound(true);
						return true;
					}
				}
			}

			return true;
		}

		@Override
		public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
			super.touchUp(event, x, y, pointer, button);
			if (((Act) event.getTarget()).enabled) {
				// controLeft
				if (event.getTarget().getName().equals("controlLeft")) {
					keyLeft = false;
					return;
				}

				// controlRight
				if (event.getTarget().getName().equals("controlRight")) {
					keyRight = false;
					return;
				}

				// each button
				if (event.getTarget().getName().substring(0, Math.min(3, event.getTarget().getName().length())).equals("btn"))
					((Act) event.getTarget()).brightness = 1;

				// if actor in focus
				if (stage.hit(event.getStageX(), event.getStageY(), true) == event.getTarget()) {
					// btnPause
					if (event.getTarget().getName().equals("btnPause")) {
						isPaused = true;
						groupPause.setVisible(true);
						btnPause.setVisible(false);
						btnRestart.setVisible(false);
						driveSound(false);
						motorSound(false);
						return;
					}

					// btnSignIn
					if (event.getTarget().getName().equals("btnSign")) {
						if (isSigned)
							nativePlatform.signOut();
						else
							nativePlatform.signIn();
						return;
					}

					// btnLeaders
					if (event.getTarget().getName().equals("btnLeaders")) {
						nativePlatform.showLeaders();
						return;
					}

					// btnStart, btnRestart
					if (event.getTarget().getName().equals("btnStart") || event.getTarget().getName().equals("btnRestart")) {
						loadScreen("game");
						return;
					}

					// btnSound
					if (event.getTarget().getName().equals("btnSound")) {
						if (pref.getBoolean("mute", false)) {
							// sound
							pref.putBoolean("mute", false);
							pref.flush();
							btnSound.tex = new TextureRegion(assetManager.get("btnMute.png", Texture.class));
							bgSound();
							if (screen.equals("main"))
								driveSound(true);
						} else {
							// mute
							pref.putBoolean("mute", true);
							pref.flush();
							btnSound.tex = new TextureRegion(assetManager.get("btnSound.png", Texture.class));
							sndBg.pause();
							driveSound(false);
							currentVolume = 0;
						}
						return;
					}

					// btnQuit
					if (event.getTarget().getName().equals("btnQuit")) {
						if (screen.equals("main"))
							Gdx.app.exit();
						else if (screen.equals("game"))
							loadScreen("main");
						return;
					}

					// btnResume
					if (event.getTarget().getName().equals("btnResume")) {
						isPaused = false;
						groupPause.setVisible(false);
						btnPause.setVisible(true);
						btnRestart.setVisible(true);
						driveSound(true);
						return;
					}
				}
			}
		}

		@Override
		public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
			super.enter(event, x, y, pointer, fromActor);

			// mouse over button
			if (((Act) event.getTarget()).enabled
					&& event.getTarget().getName().substring(0, Math.min(3, event.getTarget().getName().length())).equals("btn"))
				((Act) event.getTarget()).brightness = BRIGHTNESS_PRESSED;
		}

		@Override
		public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
			super.exit(event, x, y, pointer, toActor);

			// mouse out button
			if (event.getTarget().getName().substring(0, Math.min(3, event.getTarget().getName().length())).equals("btn"))
				((Act) event.getTarget()).brightness = 1;
		}

		@Override
		public boolean keyDown(InputEvent event, int keycode) {
			if (screen.equals("game") && hero.enabled)
				switch (keycode) {
				case Keys.LEFT:
					keyLeft = true;
					motorSound(true);
					break;
				case Keys.RIGHT:
					keyRight = true;
					motorSound(true);
					break;
				}

			return true;
		}

		@Override
		public boolean keyUp(InputEvent event, int keycode) {
			switch (keycode) {
			case Keys.LEFT:
				if (hero.enabled)
					keyLeft = false;
				break;
			case Keys.RIGHT:
				if (hero.enabled)
					keyRight = false;
				break;
			case Keys.ESCAPE: // exit from fullscreen mode
				if (Gdx.graphics.isFullscreen())
					Gdx.graphics.setDisplayMode(currentWidth, currentHeight, false);
				break;
			case Keys.ENTER: // switch to fullscreen mode
				if (!Gdx.graphics.isFullscreen())
					Gdx.graphics.setDisplayMode(Gdx.graphics.getDesktopDisplayMode().width,
							Gdx.graphics.getDesktopDisplayMode().height, true);
				break;
			case Keys.BACK: // back
				if (screen.equals("game"))
					loadScreen("main");
				else
					Gdx.app.exit();
				break;
			}

			return true;
		}
	}

	// addHero
	void addHero() {
		// hero
		hero = Lib.addLayer("hero", map, stage.getRoot(), 0).first();
		hero.effect = effect;

		// tires
		tireFront = Lib.addLayer("tireFront", map, stage.getRoot(), 0).first();
		tireRear = Lib.addLayer("tireRear", map, stage.getRoot(), 0).first();

		// joint tireRear
		WheelJointDef joint = new WheelJointDef();
		joint.bodyA = hero.body;
		joint.bodyB = tireRear.body;
		joint.localAnchorA.set(-95 / PPM, -57 / PPM);
		joint.localAnchorB.set(0, 0);
		joint.localAxisA.set(0, 0.6f);
		joint.frequencyHz = 3;
		joint.collideConnected = false;
		world.createJoint(joint);

		// joint tireFront
		joint.bodyA = hero.body;
		joint.bodyB = tireFront.body;
		joint.localAnchorA.set(95 / PPM, -53 / PPM);
		joint.localAnchorB.set(0, 0);
		joint.localAxisA.set(0, 0.6f);
		joint.frequencyHz = 3;
		joint.collideConnected = false;
		world.createJoint(joint);
	}

	// makeAnchors
	void makeAnchors(Act actor) {
		// joint left
		WheelJointDef joint = new WheelJointDef();
		joint.bodyA = actor.body;
		joint.bodyB = Lib.addBox("", stage.getRoot(), actor.body.getPosition().x - actor.getWidth() / 2 / PPM,
				actor.body.getPosition().y, 0, 0, 0, BodyType.StaticBody, 0, 0, 0, true, Lib.categoryBits[0],
				Lib.categoryBits[Lib.categoryBits.length - 1]).body;
		joint.localAnchorA.set(-actor.getWidth() / 2 / PPM, 0);
		joint.localAnchorB.set(0, 0);
		joint.collideConnected = false;
		joint.localAxisA.set(0, 0.8f);
		joint.frequencyHz = 3;
		world.createJoint(joint);

		// joint right
		joint = new WheelJointDef();
		joint.bodyA = actor.body;

		joint.bodyB = Lib.addBox("", stage.getRoot(), actor.body.getPosition().x + actor.getWidth() / 2 / PPM,
				actor.body.getPosition().y, 0, 0, 0, BodyType.StaticBody, 0, 0, 0, true, Lib.categoryBits[0],
				Lib.categoryBits[Lib.categoryBits.length - 1]).body;
		joint.localAnchorA.set(actor.getWidth() / 2 / PPM, 0);
		joint.localAnchorB.set(0, 0);
		joint.collideConnected = false;
		joint.localAxisA.set(0, 0.8f);
		joint.frequencyHz = 3;
		world.createJoint(joint);
	}

	// driveSound
	void driveSound(boolean active) {
		if (active && !pref.getBoolean("mute", false) && isForeground && !screen.isEmpty() && hero.enabled && !isPaused)
			sndDrive.loop(0.2f);
		else
			sndDrive.stop();
	}

	// motorSound
	void motorSound(boolean active) {
		sndMotor.stop();
		if (active && !pref.getBoolean("mute", false) && isForeground && !screen.isEmpty() && hero.enabled && !isPaused)
			sndMotor.play(0.4f);
	}
}