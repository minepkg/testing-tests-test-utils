package io.minepkg.testutils;

import org.spongepowered.asm.mixin.Overwrite;

import io.github.cottonmc.cotton.gui.client.BackgroundPainter;
import io.github.cottonmc.cotton.gui.client.LightweightGuiDescription;
import io.github.cottonmc.cotton.gui.widget.WPlainPanel;
import io.github.cottonmc.cotton.gui.widget.WSlider;
import io.github.cottonmc.cotton.gui.widget.WSprite;
import io.github.cottonmc.cotton.gui.widget.WToggleButton;
import io.github.cottonmc.cotton.gui.widget.data.Alignment;
import io.github.cottonmc.cotton.gui.widget.data.Axis;
import io.github.cottonmc.cotton.gui.widget.data.Color.RGB;
import io.minepkg.testutils.gui.ScreenDrawingTweak;
import io.minepkg.testutils.gui.WClickableLabel;
import io.minepkg.testutils.gui.WGradient;
import io.minepkg.testutils.gui.WSpriteButton;
import io.minepkg.testutils.gui.WTiledSprite;
import io.minepkg.testutils.gui.WUsableClippedPanel;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.network.ClientSidePacketRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Identifier;
import net.minecraft.util.PacketByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

class WRainy extends WTiledSprite {
  public float opacity = 1;
  private int step = 0;

  public WRainy(Identifier image, int tileWidth, int tileHeight) {
    super(image, tileWidth, tileHeight);
  }

  public void paintFrame(int x, int y, Identifier frame) {
    ScreenDrawingTweak.texturedRect(
      x,
      y + (step / 13),
      // but using the set tileWidth and tileHeight instead of the full height and width
      tileWidth,
      tileHeight,
      // inherited texture from WSprite. only supports the first frame (no animated sprites)
      frame,
      tint,
      opacity
    );
    // step = (step + 1) % (120 * 13);
  }
}

class WEnvMonitor extends WUsableClippedPanel {
  private static Identifier SNOW_GRASS = new Identifier("minecraft:textures/block/grass_block_snow.png");
  private static Identifier NORMAL_GRASS = new Identifier("minecraft:textures/block/grass_block_side.png");

  private boolean canSnow = false;
  WSprite sun = new WSprite(new Identifier("testutils:sun_env.png"));
  WSprite mun = new WSprite(new Identifier("testutils:mun_env.png"));
  WGradient bg = new WGradient();
  WTiledSprite grass = new WTiledSprite(NORMAL_GRASS, 12, 12);
  WRainy rain = new WRainy(new Identifier("minecraft:textures/environment/rain.png"), 24, 120);
  WRainy snow = new WRainy(new Identifier("minecraft:textures/environment/snow.png"), 24, 120);

  RGB topDayColor = new RGB(0xFF_8cb6fc);
  RGB bottomDayColor = new RGB(0xFF_b5d1ff);

  RGB topNightColor = new RGB(0xFF_00081a);
  RGB bottomNightColor = new RGB(0xFF_000e2d);

  WEnvMonitor() {
    setBackgroundPainter(BackgroundPainter.SLOT);

    add(bg, 0, 0, 200, 35);

    add(snow, 0, 0, 240, 35);
    add(rain, 0, 0, 240, 35);

    add(sun, 0, 0, 10, 10);
    add(mun, 0, 0, 10, 10);
    add(grass, 0, 23, 100, 12);
    // add(snowLayer, 0, 20, 100, 1);
  }

  public void setTimeOfDay(long time) {
    double offset = 3800;

    double realPercent = (double)time / (24000);
    double percent = ((double)time + offset) / (24000);

    double sunPercent = realPercent > 0.6 ? realPercent : realPercent * 0.80;

    double x = Math.cos((sunPercent + 0.55) * Math.PI * 2) * 42;
    double y = Math.sin((sunPercent + 0.55) * Math.PI * 2) * 21;
    sun.setLocation((int) x + 41, (int) y+ 18);

    double xMun = Math.cos((realPercent) * Math.PI * 2) * 41;
    double yMun = Math.sin((realPercent) * Math.PI * 2) * 20;
    mun.setLocation((int) xMun + 41, (int) yMun+ 22);

    if (percent <= 1) {
      bg.colorFrom = WGradient.interpolateColors(topDayColor, topNightColor, percent);
      bg.colorTo = WGradient.interpolateColors(bottomDayColor, bottomNightColor, percent);

      grass.setTint(WGradient.interpolateColors(new RGB(0xFF_FFFFFF), new RGB(0xFF_222222), percent).toRgb());
    } else {
      bg.colorFrom = WGradient.interpolateColors(bottomNightColor, topDayColor, (percent - 1) * 3);
      bg.colorTo = WGradient.interpolateColors(bottomNightColor, new RGB(0xFF_ffde78), (percent - 1) * 3);

      grass.setTint(WGradient.interpolateColors(new RGB(0xFF_222222), new RGB(0xFF_FFFFFF), (percent - 1) * 3).toRgb());
    }
  }

  public void setRain(boolean raining) {
    // HACK: modify size until i figure out how to fade that stuff
    if (!raining) {
      snow.setSize(0, 0);
      rain.setSize(0, 0);
      grass.setImage(NORMAL_GRASS);
      return;
    }
    if (this.canSnow) {
      snow.setSize(200, 35);
      rain.setSize(0, 0);
      grass.setImage(SNOW_GRASS);
    } else {
      snow.setSize(0, 0);
      rain.setSize(200, 35);
      grass.setImage(NORMAL_GRASS);
    }
  }

  public void setSnowBiome(boolean canSnow) {
    this.canSnow = canSnow;
  }

  public void setRainGradient(float gradient) {
    this.snow.opacity = gradient;
    this.rain.opacity = gradient;
  }
}

public class RuleBookGUI extends LightweightGuiDescription {
  World world;
  long timeOfDay;
  int preventTickUpdates = 0;

  WPlainPanel root = new WPlainPanel();
  WSlider timeSlider = new WSlider(0, 23999, Axis.HORIZONTAL);
  WEnvMonitor envBox = new WEnvMonitor();

  WToggleButton btnLockTime = new WToggleButton(new LiteralText("freeze time"));
  WToggleButton btnLockWeather = new WToggleButton(new LiteralText("lock weather"));

  WClickableLabel sliderDayLabel = new WClickableLabel("Day");
  WClickableLabel sliderNightLabel = new WClickableLabel("Night");

  public RuleBookGUI(World w, PlayerEntity player) {
    this.timeOfDay = w.getTimeOfDay();
    this.world = w;

    BlockPos playerPos = player.getBlockPos();
    Biome bio = w.getBiome(player.getBlockPos());

    if (bio.getTemperature(playerPos) >= 0.15F) {
      this.envBox.setSnowBiome(false);
    } else {
      this.envBox.setSnowBiome(playerPos.getY() >= 0 && playerPos.getY() < 256);
    }
    this.envBox.setRain(world.isRaining());

    setRootPanel(root);
    root.setSize(100, 100);

    sliderDayLabel.setAlignment(Alignment.CENTER);
    sliderNightLabel.setAlignment(Alignment.CENTER);

    sliderDayLabel.setOnClick(() -> setTime(5500));
    sliderNightLabel.setOnClick(() -> setTime(18500));

    WSpriteButton btnWeatherClear = new WSpriteButton(new Identifier("testutils:sun.png"));
    WSpriteButton btnWeatherRain = new WSpriteButton(new Identifier("testutils:rain.png"));
    WSpriteButton btnWeatherThunder = new WSpriteButton(new Identifier("testutils:thunder.png"));

    btnWeatherClear.setOnClick(() -> setWeather(TestUtils.WEATHER_CLEAR));
    btnWeatherRain.setOnClick(() -> setWeather(TestUtils.WEATHER_RAIN));
    btnWeatherThunder.setOnClick(() -> setWeather(TestUtils.WEATHER_THUNDER));

    envBox.add(btnWeatherClear, 87, -1, 13, 13);
    envBox.add(btnWeatherRain, 87, 11, 13, 13);
    envBox.add(btnWeatherThunder, 87, 23, 13, 13);

    // add all time controls to root panel
    root.add(envBox, 0, 0, 6, 2);
    root.add(timeSlider, 0, 35, 100, 20);
    root.add(sliderDayLabel, 0, 52, 50, 20);
    root.add(sliderNightLabel, 50, 52, 50, 20);

    // add lock controls
    root.add(btnLockTime, 0, 70, 50, 20);
    root.add(btnLockWeather, 0, 85, 90, 20);

    // set initial timeOfDay
    int timeOfDay = (int)w.getTimeOfDay() % 24000;
    timeSlider.setValue(timeOfDay);
    envBox.setTimeOfDay(timeOfDay);

    btnLockTime.setToggle(!w.getGameRules().getBoolean(GameRules.DO_DAYLIGHT_CYCLE));
    btnLockWeather.setToggle(!w.getGameRules().getBoolean(GameRules.DO_WEATHER_CYCLE));

    timeSlider.setValueChangeListener((time) -> {
      // no tick updates while dragging (bit of a hack)
      this.preventTickUpdates = 1000;
      envBox.setTimeOfDay(time);
    });
    timeSlider.setDraggingFinishedListener((value)-> {
      setTime((long) value);
    });

    // enabling the button locks the time
    btnLockTime.setOnToggle((toggled) -> setRule(TestUtils.DO_DAYLIGHT_CYCLE_RULE, !toggled));

    // enabling the button locks the weather
    btnLockWeather.setOnToggle((toggled) -> setRule(TestUtils.DO_WEATHER_CYCLE_RULE, !toggled));

    // can't do this sooner, no idea why
    envBox.setSize(100, 35);
    root.validate(this);
  }

  public void tick() {
    this.envBox.setRainGradient(world.getRainGradient(1.0F));
    // do not update stuff if currently dragging
    // this is a bit hacky, but tick timing can be hard
    if (this.preventTickUpdates > 0) {
      this.preventTickUpdates--;
      return;
    }
    long time = world.getTimeOfDay() % 24000;
    this.envBox.setTimeOfDay(time);
    this.envBox.setRain(world.isRaining());
    this.timeSlider.setValue((int)time);

    // update if someone else changed the game rules
    btnLockTime.setToggle(!world.getGameRules().getBoolean(GameRules.DO_DAYLIGHT_CYCLE));
    btnLockWeather.setToggle(!world.getGameRules().getBoolean(GameRules.DO_WEATHER_CYCLE));
  }

  private void setTime(long timeOfDay) {
    preventTickUpdates = 1000;
    PacketByteBuf passedData = new PacketByteBuf(Unpooled.buffer());
    passedData.writeLong(timeOfDay);
    // Send packet to server to change the time
    ClientSidePacketRegistry.INSTANCE.sendToServer(TestUtils.SET_TIME_PACKET_ID, passedData);
    timeSlider.setValue((int)timeOfDay, false);
    envBox.setTimeOfDay(timeOfDay);
    // TODO: wait for response instead
    preventTickUpdates = 20;
  }

  private void setRule(short id, boolean value) {
    preventTickUpdates += 1;
    PacketByteBuf passedData = new PacketByteBuf(Unpooled.buffer());
    // eg. 0 is day cycle
    passedData.writeShort(id);
    // enabling the button locks the weather
    passedData.writeBoolean(value);
    ClientSidePacketRegistry.INSTANCE.sendToServer(TestUtils.SET_RULE_PACKET_ID, passedData);
    preventTickUpdates = 20;
  }

  private void setWeather(short weather) {
    preventTickUpdates += 1;
    PacketByteBuf passedData = new PacketByteBuf(Unpooled.buffer());
    passedData.writeShort(weather);
    ClientSidePacketRegistry.INSTANCE.sendToServer(TestUtils.SET_WEATHER_PACKET_ID, passedData);
    preventTickUpdates = 30;
  }
}
