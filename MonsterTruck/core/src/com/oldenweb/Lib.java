package com.oldenweb;

import java.io.File;

import com.badlogic.gdx.Application.ApplicationType;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.BodyDef.BodyType;
import com.badlogic.gdx.physics.box2d.ChainShape;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.Shape;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.SnapshotArray;

// custom library
public class Lib {
	static final short[] categoryBits = { 1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048, 4096, 8192, 16384, (short) 32768 }; // categoryBits

	// addLayer
	static Array<Act> addLayer(String layerName, JsonValue map, Group parent, float deltaX) {
		Array<Act> actors = new Array<Act>();
		JsonValue images = null;
		JsonValue layer;
		JsonValue objects;
		JsonValue object;
		Act actor;
		TextureRegion tex;
		BodyDef bdef;
		FixtureDef fdef;
		Body body;
		String filePath;

		// images
		if (map.has("images"))
			images = map.get("images");

		// layers
		JsonValue layers = map.get("layers");
		for (int i = 0; i < layers.size; i++) {
			layer = layers.get(i);

			// if add only one layer
			if (layerName != null && !layer.getString("name", "").equals(layerName))
				continue;

			// objects
			if (layer.has("objects")) {
				objects = layer.get("objects");

				for (int j = 0; j < objects.size; j++) {
					object = objects.get(j);
					actor = null;

					// actor
					actor = new Act(object.getString("name", layer.getString("name", "")));
					if (object.has("alpha"))
						actor.setAlpha(object.getFloat("alpha", 1));
					if (object.has("angle"))
						actor.setRotation(-object.getFloat("angle", 0));
					parent.addActor(actor);
					actors.add(actor);

					// if image exists
					if (object.getInt("image", -1) != -1) {
						// tex
						filePath = map.getString("folder", "") + images.getString(object.getInt("image", -1));
						tex = new TextureRegion(Main.assetManager.get(filePath, Texture.class));
						actor.tex = tex;

						// flip
						actor.flipX = object.getBoolean("flip_x", false) ? -1 : 1;
						actor.flipY = object.getBoolean("flip_y", false) ? -1 : 1;

						// size & center point
						actor.setSize(actor.tex.getRegionWidth(), actor.tex.getRegionHeight());
						actor.setOrigin(actor.getWidth() * 0.5f, actor.getHeight() * 0.5f);

						// position
						actor.setPosition(deltaX + object.getFloat("x", 0) - tex.getRegionWidth() * 0.5f,
								map.getInt("map_height", 0) - object.getFloat("y", 0) - tex.getRegionHeight() * 0.5f);

						// touchable
						if (object.getBoolean("touchable", false))
							actor.addListener(Main.controlListener);
					} else
						actor.setPosition(deltaX + object.getFloat("x", 0), map.getInt("map_height", 0) - object.getFloat("y", 0));

					// physics
					if (object.getBoolean("physics", false) && !object.getString("shape_type", "").isEmpty()) {
						// BodyDef
						bdef = new BodyDef();
						if (object.getString("body_type", "").equals("dynamic"))
							bdef.type = BodyType.DynamicBody;
						else if (object.getString("body_type", "").equals("kinematic"))
							bdef.type = BodyType.KinematicBody;
						else
							bdef.type = BodyType.StaticBody;
						bdef.angle = -(float) Math.toRadians(object.getFloat("angle", 0));
						bdef.fixedRotation = object.getBoolean("fixed_rotation", false);
						bdef.position.set((deltaX + object.getFloat("x", 0)) / Main.PPM,
								(map.getInt("map_height", 0) - object.getFloat("y", 0)) / Main.PPM);

						// FixtureDef
						fdef = new FixtureDef();
						fdef.density = object.getFloat("density", 0);
						fdef.friction = object.getFloat("friction", 0);
						fdef.restitution = object.getFloat("restitution", 0);
						fdef.isSensor = object.getBoolean("sensor", false);
						fdef.filter.categoryBits = categoryBits[object.getInt("category_bit", 1) - 1];

						// maskBits
						if (object.has("mask_bits")) {
							int[] bitsArray = object.get("mask_bits").asIntArray();
							if (bitsArray.length != 0) {
								fdef.filter.maskBits = 0;
								for (int n = 0; n < bitsArray.length; n++)
									fdef.filter.maskBits += categoryBits[bitsArray[n] - 1];
							}
						}

						// body
						body = Main.world.createBody(bdef);
						body.setUserData(actor);
						actor.body = body;

						// shape
						if (object.has("shape_separate")) {
							for (int k = 0; k < object.get("shape_separate").size; k++) {
								fdef.shape = getShapeSeparate(object, k);
								body.createFixture(fdef);
							}
						} else {
							fdef.shape = getShape(object);
							body.createFixture(fdef);
						}

						// linear velocity
						body.setLinearVelocity(object.getFloat("velocity_x", 0), -object.getFloat("velocity_y", 0));
					}
				}
			}

			// if add only one layer
			if (layerName != null && layer.getString("name", "").equals(layerName))
				break;
		}

		return actors;
	}

	// addGroup
	static Group addGroup(String name, JsonValue map, Group parent, float deltaX) {
		Group group = new Group();
		group.setName(name);
		parent.addActor(group);
		addLayer(name, map, group, deltaX);
		return group;
	}

	// addBox
	static Act addBox(String name, Group parent, float x, float y, float width, float height, float angle, BodyType type,
			float density, float friction, float restitution, boolean isSensor, int categoryBits, int maskBits) {
		// BodyDef
		BodyDef bdef = new BodyDef();
		bdef.type = type;
		bdef.position.set(x, y);
		bdef.angle = (float) Math.toRadians(angle);

		// PolygonShape
		PolygonShape shape = new PolygonShape();
		shape.setAsBox(width * 0.5f / Main.PPM, height * 0.5f / Main.PPM);

		// FixtureDef
		FixtureDef fdef = new FixtureDef();
		fdef.shape = shape;
		fdef.density = density;
		fdef.friction = friction;
		fdef.restitution = restitution;
		fdef.isSensor = isSensor;
		fdef.filter.categoryBits = (short) categoryBits;
		fdef.filter.maskBits = (short) maskBits;

		// body
		Body body = Main.world.createBody(bdef);
		body.createFixture(fdef);

		// actor
		Act actor = new Act(name);
		actor.body = body;
		parent.addActor(actor);
		body.setUserData(actor);

		return actor;
	}

	// addCircle
	static Act addCircle(String name, Group parent, float x, float y, float diameter, float angle, BodyType type, float density,
			float friction, float restitution, boolean isSensor, int categoryBits, int maskBits) {
		// BodyDef
		BodyDef bdef = new BodyDef();
		bdef.type = type;
		bdef.position.set(x, y);
		bdef.angle = (float) Math.toRadians(angle);

		// CircleShape
		CircleShape shape = new CircleShape();
		shape.setRadius(diameter * 0.5f / Main.PPM);

		// FixtureDef
		FixtureDef fdef = new FixtureDef();
		fdef.shape = shape;
		fdef.density = density;
		fdef.friction = friction;
		fdef.restitution = restitution;
		fdef.isSensor = isSensor;
		fdef.filter.categoryBits = (short) categoryBits;
		fdef.filter.maskBits = (short) maskBits;

		// body
		Body body = Main.world.createBody(bdef);
		body.createFixture(fdef);

		// actor
		Act actor = new Act(name);
		actor.body = body;
		parent.addActor(actor);
		body.setUserData(actor);

		return actor;
	}

	// getShape
	static Shape getShape(JsonValue object) {
		float[] shape_values = object.get("shape_values").asFloatArray();
		Shape shape = null;

		if (object.getString("shape_type", "").equals("circle")) {
			// circle
			shape = new CircleShape();
			((CircleShape) shape).setRadius(shape_values[2] * 0.5f / Main.PPM);
			((CircleShape) shape).setPosition(new Vector2(shape_values[0] / Main.PPM, -shape_values[1] / Main.PPM));
		} else if (object.getString("shape_type", "").equals("rectangle")) {
			// rectangle
			shape = new PolygonShape();
			((PolygonShape) shape).setAsBox(shape_values[2] * 0.5f / Main.PPM, shape_values[3] * 0.5f / Main.PPM, new Vector2(
					shape_values[0] / Main.PPM, -shape_values[1] / Main.PPM), 0);
		} else if (object.getString("shape_type", "").equals("polygon")) {
			// polygon
			shape = new PolygonShape();
			Vector2[] vertices = new Vector2[shape_values.length / 2];
			for (int i = 0; i < shape_values.length / 2; i++)
				vertices[i] = new Vector2(shape_values[i * 2] / Main.PPM, -shape_values[i * 2 + 1] / Main.PPM);
			((PolygonShape) shape).set(vertices);
		} else if (object.getString("shape_type", "").equals("polyline")) {
			// polyline
			shape = new ChainShape();
			Vector2[] vertices = new Vector2[shape_values.length / 2];
			for (int i = 0; i < shape_values.length / 2; i++)
				vertices[i] = new Vector2(shape_values[i * 2] / Main.PPM, -shape_values[i * 2 + 1] / Main.PPM);
			((ChainShape) shape).createChain(vertices);
		}

		return shape;
	}

	// getShapeSeparate
	static Shape getShapeSeparate(JsonValue object, int numPolygon) {
		float[] shape_values = object.get("shape_separate").get(numPolygon).asFloatArray();
		Shape shape = new PolygonShape();

		Vector2[] vertices = new Vector2[shape_values.length / 2];
		for (int i = 0; i < shape_values.length / 2; i++)
			vertices[i] = new Vector2(shape_values[i * 2] / Main.PPM, -shape_values[i * 2 + 1] / Main.PPM);
		((PolygonShape) shape).set(vertices);

		return shape;
	}

	// getActors
	static Array<Act> getActors(String actorName, Group parent) {
		Array<Act> actors = new Array<Act>();
		SnapshotArray<Actor> allActors = parent.getChildren();

		for (int i = 0; i < allActors.size; i++)
			if (allActors.get(i).getName().equals(actorName) || actorName == null)
				actors.add((Act) allActors.get(i));

		return actors;
	}

	// loadAssets
	static void loadAssets(boolean debug) {
		// auto load assets from assets folder
		FileHandle[] list;
		if (Gdx.app.getType().equals(ApplicationType.Desktop))
			list = Gdx.files.internal("./bin").exists() ? Gdx.files.internal("./bin").list() : Gdx.files.local("").list();
		else
			list = Gdx.files.internal("").list();

		for (int i = 0; i < list.length; i++)
			if (list[i].nameWithoutExtension().equals("sndBg")) {
				Main.assetManager.load(list[i].name(), Music.class);
				if (debug)
					log("Main.assetManager.load(\"" + list[i].name() + "\", Music.class);");
			} else if (list[i].extension().equalsIgnoreCase("mp3") || list[i].extension().equalsIgnoreCase("wav")
					|| list[i].extension().equalsIgnoreCase("ogg")) {
				Main.assetManager.load(list[i].name(), Sound.class);
				if (debug)
					log("Main.assetManager.load(\"" + list[i].name() + "\", Sound.class);");
			} else if ((list[i].extension().equalsIgnoreCase("png") || list[i].extension().equalsIgnoreCase("jpg")
					|| list[i].extension().equalsIgnoreCase("jpeg") || list[i].extension().equalsIgnoreCase("bmp") || list[i]
					.extension().equalsIgnoreCase("gif"))
					&& !new File(list[i].pathWithoutExtension() + ".atlas").exists()
					&& !new File(list[i].pathWithoutExtension() + ".fnt").exists()
					&& !new File(list[i].pathWithoutExtension()).exists()) {
				Main.assetManager.load(list[i].name(), Texture.class);
				if (debug)
					log("Main.assetManager.load(\"" + list[i].name() + "\", Texture.class);");
			} else if (list[i].extension().equalsIgnoreCase("atlas")) {
				Main.assetManager.load(list[i].name(), TextureAtlas.class);
				if (debug)
					log("Main.assetManager.load(\"" + list[i].name() + "\", TextureAtlas.class);");
			} else if (list[i].extension().equalsIgnoreCase("fnt")) {
				Main.assetManager.load(list[i].name(), BitmapFont.class);
				if (debug)
					log("Main.assetManager.load(\"" + list[i].name() + "\", BitmapFont.class);");
			}

		// loading first
		Main.assetManager.finishLoadingAsset("loading.png");
		Main.assetManager.get("loading.png", Texture.class).setFilter(TextureFilter.Linear, TextureFilter.Linear);
	}

	// loadMapAssets
	/*-static void loadMapAssets(JsonValue map) {
		if (map.has("images")) {
			JsonValue images = map.get("images");
			for (int i = 0; i < images.size; i++) {
				String filePath = map.getString("folder", "") + images.getString(i);
				Main.assetManager.load(filePath, Texture.class);
			}
		}

		Main.assetManager.finishLoading();
		texturesFilter(); // textures smoothing
	}*/

	// texturesFilter
	static void texturesFilter() {
		// atlas
		Array<TextureAtlas> arrayAtlas = new Array<TextureAtlas>();
		Array<AtlasRegion> arrayRegion = new Array<AtlasRegion>();
		Main.assetManager.getAll(TextureAtlas.class, arrayAtlas);
		for (TextureAtlas atlas : arrayAtlas) {
			arrayRegion = atlas.getRegions();
			for (AtlasRegion region : arrayRegion) {
				region.getTexture().setFilter(TextureFilter.Linear, TextureFilter.Linear);
			}
		}
		arrayAtlas = null;
		arrayRegion = null;

		// texture
		Array<Texture> arrayTexture = new Array<Texture>();
		Main.assetManager.getAll(Texture.class, arrayTexture);
		for (Texture texture : arrayTexture) {
			texture.setFilter(TextureFilter.Linear, TextureFilter.Linear);
		}
		arrayTexture = null;

		// font
		Array<BitmapFont> arrayFont = new Array<BitmapFont>();
		Main.assetManager.getAll(BitmapFont.class, arrayFont);
		for (BitmapFont font : arrayFont) {
			font.getRegion().getTexture().setFilter(TextureFilter.Linear, TextureFilter.Linear);
		}
		arrayFont = null;
	}

	// convert seconds to d:hh:mm:ss
	static String timeConvert(int t) {
		String str = "";
		int d, h, m, s;

		if (t / 86400 >= 1) {// if day exist
			d = t / 86400;
			str += d + ":";
		} else {
			d = 0;
			// str += "00:";
		}

		t = t - (86400 * d);

		if (t / 3600 >= 1) {// if hour exist
			h = t / 3600;
			if (h < 10 && d > 0) {
				str += "0";
			}
			str += h + ":";
		} else {
			h = 0;
			// str += "00:";
		}

		if ((t - h * 3600) / 60 >= 1) {// if minute exist
			m = (t - h * 3600) / 60;
			s = (t - h * 3600) - m * 60;
			if (m < 10 && h > 0) {
				str += "0";
			}
			str += m + ":";
		} else {
			m = 0;
			s = t - h * 3600;
			// str += "00:";
		}

		if (s < 10 && m > 0) {
			str += "0";
		}
		str += s;

		return str;
	}

	// customRound
	static float customRound(float num, float decimal) {
		return Math.round(num * decimal) / decimal;
	}

	// log
	static void log(Object obj) {
		if (Gdx.app.getType().equals(ApplicationType.Desktop))
			System.out.println(obj);
		else
			Gdx.app.log("@", obj.toString());
	}
}