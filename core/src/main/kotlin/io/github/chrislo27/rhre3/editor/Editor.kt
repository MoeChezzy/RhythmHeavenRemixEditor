package io.github.chrislo27.rhre3.editor

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Disposable
import io.github.chrislo27.rhre3.RHRE3
import io.github.chrislo27.rhre3.RHRE3Application
import io.github.chrislo27.rhre3.editor.stage.EditorStage
import io.github.chrislo27.rhre3.oopsies.ReversibleAction
import io.github.chrislo27.rhre3.theme.DarkTheme
import io.github.chrislo27.rhre3.theme.Theme
import io.github.chrislo27.rhre3.track.PlayState
import io.github.chrislo27.rhre3.track.Remix
import io.github.chrislo27.toolboks.i18n.Localization
import io.github.chrislo27.toolboks.registry.AssetRegistry
import io.github.chrislo27.toolboks.util.MathHelper
import io.github.chrislo27.toolboks.util.gdxutils.*


class Editor(val main: RHRE3Application, stageCamera: OrthographicCamera)
    : Disposable, InputProcessor {

    companion object {
        const val ENTITY_HEIGHT: Float = 48f
        const val ENTITY_WIDTH: Float = ENTITY_HEIGHT * 4

        const val ICON_SIZE: Float = 32f
        const val ICON_PADDING: Float = 6f
        const val ICON_COUNT_X: Int = 13
        const val ICON_COUNT_Y: Int = 4

        const val TRACK_COUNT: Int = 5
        const val TRACK_LINE: Float = 2f
        const val PATTERN_COUNT: Int = 5

        const val MESSAGE_BAR_HEIGHT: Int = 14
        const val BUTTON_SIZE: Float = 32f
        const val BUTTON_PADDING: Float = 4f
        const val BUTTON_BAR_HEIGHT: Float = BUTTON_SIZE + BUTTON_PADDING * 2

        val TRANSLUCENT_BLACK: Color = Color(0f, 0f, 0f, 0.5f)
        val ARROWS: List<String> = listOf("▲", "▼", "△", "▽", "➡")
        val SELECTED_TINT: Color = Color(0.65f, 1f, 1f, 1f)
        val CUE_PATTERN_COLOR: Color = Color(0.65f, 0.65f, 0.65f, 1f)
    }

    val camera: OrthographicCamera by lazy {
        val c = OrthographicCamera()
        resizeCamera(c)
        c.position.x = 0f
        c.update()
        c
    }

    val pickerSelection: PickerSelection = PickerSelection()
    var remix: Remix = Remix(camera, this)
    val stage: EditorStage = EditorStage(
            null, stageCamera, main, this)
    val batch: SpriteBatch
        get() = main.batch
    var theme: Theme = DarkTheme()
    val subbeatSection = SubbeatSection()
    var snap: Float = 0.25f

    sealed class ClickOccupation {

        interface TrackerBased {
            var finished: Boolean
            var final: Float
        }

        object None : ClickOccupation()

        class Playback(val editor: Editor) : ClickOccupation(), ReversibleAction<Remix>, TrackerBased {
            val old = editor.remix.playbackStart
            override var finished: Boolean = false
            override var final: Float = Float.NaN
                set(value) {
                    if (!java.lang.Float.isNaN(field)) {
                        error("Attempt to set value to $value when already set to $field")
                    }
                    field = value
                }

            override fun redo(context: Remix) {
                if (final == Float.NaN)
                    error("Final value was NaN which is impossible")
                editor.remix.playbackStart = final
            }

            override fun undo(context: Remix) {
                editor.remix.playbackStart = old
            }
        }

        class Music(val editor: Editor) : ClickOccupation(), ReversibleAction<Remix>, TrackerBased {
            val old = editor.remix.musicStartSec
            override var finished: Boolean = false
            override var final: Float = Float.NaN
                set(value) {
                    if (!java.lang.Float.isNaN(field)) {
                        error("Attempt to set value to $value when already set to $field")
                    }
                    field = value
                }

            override fun redo(context: Remix) {
                if (final == Float.NaN)
                    error("Final value was NaN which is impossible")
                editor.remix.musicStartSec = final
            }

            override fun undo(context: Remix) {
                editor.remix.musicStartSec = old
            }
        }

        class SelectionDrag(val editor: Editor) : ClickOccupation()
    }

    private var clickOccupation: ClickOccupation = ClickOccupation.None

    fun toScaleX(float: Float): Float =
            (float / RHRE3.WIDTH) * camera.viewportWidth

    fun toScaleY(float: Float): Float =
            (float / RHRE3.HEIGHT) * camera.viewportHeight

    fun BitmapFont.scaleFont() {
        this.setUseIntegerPositions(false)
        this.data.setScale(camera.viewportWidth / main.defaultCamera.viewportWidth,
                           camera.viewportHeight / main.defaultCamera.viewportHeight)
    }

    fun BitmapFont.unscaleFont() {
        this.setUseIntegerPositions(true)
        this.data.setScale(1f)
    }

    fun getBeatRange(): IntRange =
            Math.round((camera.position.x - camera.viewportWidth / 2 * camera.zoom) / toScaleX(
                    ENTITY_WIDTH)) - 1..(Math.round(
                    (camera.position.x + camera.viewportWidth / 2 * camera.zoom) / toScaleX(ENTITY_WIDTH)) + 1)

    /**
     * Pre-stage render.
     */
    fun render() {
        val bgColour = theme.background
        Gdx.gl.glClearColor(bgColour.r, bgColour.g, bgColour.b, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        camera.position.y = 1f
        camera.update()
        batch.projectionMatrix = camera.combined
        batch.begin()

        val beatRange = getBeatRange()
        val font = main.defaultFont
        val trackYOffset = toScaleY(-TRACK_LINE / 2f)

        font.scaleFont()

        // horizontal track lines
        run trackLines@ {
            batch.color = theme.trackLine
            val startX = beatRange.start.toFloat()
            val width = beatRange.endInclusive.toFloat() - startX
            for (i in 0..TRACK_COUNT) {
                batch.fillRect(startX, trackYOffset + i.toFloat(), width,
                               toScaleY(TRACK_LINE))
            }
            batch.setColor(1f, 1f, 1f, 1f)
        }

        remix.entities.forEach {
            if (it.bounds.x + it.bounds.width >= beatRange.start && it.bounds.x <= beatRange.endInclusive) {
                it.render(batch)
            }
        }

        // beat lines
        run beatLines@ {
            for (i in beatRange) {
                // TODO time signature based
                if (i % 4 == 0) {
                    batch.color = theme.trackLine
                } else {
                    batch.setColor(theme.trackLine.r, theme.trackLine.g, theme.trackLine.b, theme.trackLine.a * 0.25f)
                }
                batch.fillRect(i.toFloat(), trackYOffset, toScaleX(TRACK_LINE),
                               TRACK_COUNT + toScaleY(TRACK_LINE))

                if (subbeatSection.enabled && i in subbeatSection.start..subbeatSection.end) {
                    batch.setColor(theme.trackLine.r, theme.trackLine.g, theme.trackLine.b, theme.trackLine.a * 0.35f)
                    for (j in 1 until Math.round(1f / snap)) {
                        batch.fillRect(i.toFloat() + snap * j, trackYOffset, toScaleX(TRACK_LINE),
                                       TRACK_COUNT + toScaleY(TRACK_LINE))
                    }
                }
            }
            batch.setColor(1f, 1f, 1f, 1f)
        }

        // trackers (playback start, music, others)
        run trackers@ {
            val font = main.defaultBorderedFont
            val oldFontColor = font.color

            fun getTrackerTime(beat: Float): String {
                val sec = Math.abs(remix.tempos.beatsToSeconds(beat))
                return Localization["tracker.any.time",
                        "%.3f".format(beat),
                        (if (beat < 0) "-" else "") +
                                "%1$02d:%2$06.3f".format((sec / 60).toInt(), sec % 60)]
            }

            fun renderAboveTracker(textKey: String?, controlKey: String?, units: Int, beat: Float, color: Color,
                                   triangleHeight: Float = 0.4f) {
                val triangleWidth = toScaleX(triangleHeight * ENTITY_HEIGHT)
                val x = beat
                val y = trackYOffset
                val height = (TRACK_COUNT + 1.25f + 1.2f * units) + toScaleY(TRACK_LINE)
                batch.setColor(color.toFloatBits())
                batch.fillRect(beat, y, toScaleX(TRACK_LINE * 1.5f),
                               height)
                batch.draw(AssetRegistry.get<Texture>("tracker_right_tri"),
                           x, y + height - triangleHeight, triangleWidth, triangleHeight)

                font.scaleFont()
                font.scaleMul(0.75f)
                font.color = batch.color
                if (textKey != null) {
                    font.drawCompressed(batch, Localization[textKey], x - 1.05f, y + height, 1f, Align.right)
                }
                font.drawCompressed(batch, getTrackerTime(beat), x + triangleWidth + 0.05f, y + height, 1f, Align.left)

                if (controlKey != null) {
                    val line = font.lineHeight
                    font.scaleMul(0.75f)
                    font.drawCompressed(batch, Localization[controlKey], x - 1.05f, y + height - line, 1f, Align.right)
                }

                font.scaleFont()
            }

            renderAboveTracker("tracker.music", "tracker.music.controls",
                               1, remix.tempos.secondsToBeats(remix.musicStartSec), theme.trackers.musicStart)
            renderAboveTracker("tracker.playback", "tracker.playback.controls",
                               0, remix.playbackStart, theme.trackers.playback)
            if (remix.playState != PlayState.STOPPED) {
                renderAboveTracker(null, null, 0, remix.beat,
                                   theme.trackers.playback, 0f)
            }

            font.color = oldFontColor
            font.unscaleFont()
        }

        // beat numbers
        run beatNumbers@ {
            for (i in beatRange) {
                font.color = theme.trackLine
                val width = ENTITY_WIDTH * 0.4f
                val x = i - width / 2f
                val y = TRACK_COUNT + toScaleY(TRACK_LINE + TRACK_LINE) + font.capHeight
                val text = "${Math.abs(i)}"
                font.drawCompressed(batch, text,
                                    x, y, width, Align.center)
                if (i < 0) {
                    val textWidth = font.getTextWidth(text, width, false)
                    font.drawCompressed(batch, "-", x - textWidth / 2f, y, ENTITY_WIDTH * 0.2f, Align.right)
                }

                // TODO time signature based
                if (Math.floorMod(i, 4) == 0) {
                    val measureNum = i / 4 + 1
                    if (measureNum >= 1) {
                        font.setColor(theme.trackLine.r, theme.trackLine.g, theme.trackLine.b, theme.trackLine.a * 0.5f)
                        font.drawCompressed(batch, "$measureNum",
                                            x, y + font.lineHeight, width, Align.center)
                    }
                }
            }
            font.setColor(1f, 1f, 1f, 1f)
        }

        font.unscaleFont()
        batch.end()
        batch.projectionMatrix = main.defaultCamera.combined
        batch.begin()

        batch.end()

    }

    fun postStageRender() {

    }

    fun renderUpdate() {
        val shift = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)
        val control = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(
                Input.Keys.CONTROL_RIGHT)
        val alt = Gdx.input.isKeyPressed(Input.Keys.ALT_LEFT) || Gdx.input.isKeyPressed(
                Input.Keys.ALT_RIGHT)
        val left = !stage.isTyping && Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT)
        val right = !stage.isTyping && Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT)
        val accelerateCamera = shift || control
        val cameraDelta = toScaleX(ENTITY_WIDTH * 5 * Gdx.graphics.deltaTime * if (accelerateCamera) 5 else 1)

        if (Gdx.input.isKeyJustPressed(Input.Keys.PAGE_UP)) {
            Gdx.input.inputProcessor.scrolled(-1)
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.PAGE_DOWN)) {
            Gdx.input.inputProcessor.scrolled(1)
        }

        run camera@ {
            if (left) {
                camera.position.x -= cameraDelta
            }
            if (right) {
                camera.position.x += cameraDelta
            }

            if (Gdx.input.isKeyJustPressed(Input.Keys.HOME)) {
                camera.position.x = 0f
            } else if (Gdx.input.isKeyJustPressed(Input.Keys.END)) {
                // TODO
            }
        }

        subbeatSection.enabled = false
        fun setSubbeatSectionToMouse() {
            subbeatSection.enabled = true
            subbeatSection.start = Math.floor(remix.camera.getInputX().toDouble()).toFloat()
            subbeatSection.end = subbeatSection.start
        }

        val clickOccupation = clickOccupation
        val isMusicTrackerButtonDown = (Gdx.input.isButtonPressed(Input.Buttons.MIDDLE) ||
                (control && Gdx.input.isButtonPressed(Input.Buttons.RIGHT))) && !shift
        val isPlaybackTrackerButtonDown = Gdx.input.isButtonPressed(Input.Buttons.RIGHT) && !control && !shift
        val isAnyTrackerButtonDown = isMusicTrackerButtonDown || isPlaybackTrackerButtonDown
        val isDraggingButtonDown = Gdx.input.isButtonPressed(Input.Buttons.LEFT)
        val isCopying = isDraggingButtonDown && alt
        if (clickOccupation is ClickOccupation.TrackerBased &&
                !isAnyTrackerButtonDown && clickOccupation.finished) {
            this.clickOccupation = ClickOccupation.None
        } else {
            when (clickOccupation) {
                ClickOccupation.None -> {
                    if (isMusicTrackerButtonDown) {
                        this.clickOccupation = ClickOccupation.Music(this)
                    } else if (isPlaybackTrackerButtonDown && !control) {
                        this.clickOccupation = ClickOccupation.Playback(this)
                    } else if (isDraggingButtonDown) {
                        // TODO initialize based on new, move, copy
                        if (stage.pickerStage.isMouseOver()) {
                        }
                    }
                }
                is ClickOccupation.Music -> {
                    if (isMusicTrackerButtonDown) {
                        setSubbeatSectionToMouse()
                        remix.musicStartSec = remix.tempos.beatsToSeconds(
                                MathHelper.snapToNearest(remix.camera.getInputX(), snap))
                    } else {
                        clickOccupation.finished = true
                        clickOccupation.final = remix.musicStartSec
                        remix.addActionWithoutMutating(clickOccupation)
                    }
                }
                is ClickOccupation.Playback -> {
                    if (isPlaybackTrackerButtonDown) {
                        setSubbeatSectionToMouse()
                        remix.playbackStart = MathHelper.snapToNearest(remix.camera.getInputX(), snap)
                    } else {
                        clickOccupation.finished = true
                        clickOccupation.final = remix.playbackStart
                        remix.addActionWithoutMutating(clickOccupation)
                    }
                }
                is ClickOccupation.SelectionDrag -> {
                    if (!isDraggingButtonDown) {
                        // TODO attempt place
                    } else {
                        // TODO move selection
                    }
                }
            }
        }

        // undo/redo
        if (control) {
            if (remix.canRedo() &&
                    (Gdx.input.isKeyJustPressed(Input.Keys.Y) ||
                            (shift && Gdx.input.isKeyJustPressed(Input.Keys.Z)))) {
                remix.redo()
            } else if (remix.canUndo() && Gdx.input.isKeyJustPressed(Input.Keys.Z) && !shift) {
                remix.undo()
            }
        }

    }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        println("touch")
        return true
    }

    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        return false
    }

    override fun keyDown(keycode: Int): Boolean {
        return false
    }

    override fun keyUp(keycode: Int): Boolean {
        return false
    }

    override fun scrolled(amount: Int): Boolean {
        return false
    }

    override fun mouseMoved(screenX: Int, screenY: Int): Boolean {
        return false
    }

    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
        return false
    }

    override fun keyTyped(character: Char): Boolean {
        return false
    }

    override fun dispose() {
    }

    private fun resizeCamera(camera: OrthographicCamera) {
        camera.viewportWidth = RHRE3.WIDTH / ENTITY_WIDTH
        camera.viewportHeight = RHRE3.HEIGHT / ENTITY_HEIGHT
        camera.update()
    }

    fun resize(width: Int, height: Int) {
        stage.updatePositions()
        resizeCamera(camera)
    }
}