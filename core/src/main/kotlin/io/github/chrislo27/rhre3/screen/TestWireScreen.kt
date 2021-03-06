package io.github.chrislo27.rhre3.screen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.MathUtils
import io.github.chrislo27.rhre3.RHRE3Application
import io.github.chrislo27.toolboks.ToolboksScreen
import io.github.chrislo27.toolboks.registry.AssetRegistry
import io.github.chrislo27.toolboks.util.gdxutils.isShiftDown


class TestWireScreen(main: RHRE3Application)
    : ToolboksScreen<RHRE3Application, TestWireScreen>(main), HidesVersionText {

    class Frame(var time: Float)

    val camera: OrthographicCamera = OrthographicCamera().apply {
        setToOrtho(false, 1280f, 720f)
    }
    val frames: MutableList<Frame> = mutableListOf()
    var elapsedTime: Float = 0f
    var longestTime: Float = 1f
    var shortestTime: Float = 0f

    init {
        reset()
    }

    override fun render(delta: Float) {
        super.render(delta)

        val batch = main.batch
        val width = camera.viewportWidth
        val height = camera.viewportHeight
        val tex: Texture = AssetRegistry["logo_wireframe_1024"]
        val startSize = height * 2f
        val endingSize = 512f

        batch.projectionMatrix = camera.combined
        batch.begin()
        batch.setColor(1f, 1f, 1f, MathUtils.lerp(if (frames.any { it.time <= 0f }) 0.5f else 0f, 1f, ((elapsedTime - shortestTime) / (longestTime - shortestTime)).coerceIn(0f, 1f)))

        batch.draw(tex, width / 2 - endingSize / 2, height / 2 - endingSize / 2, endingSize, endingSize)

        frames.forEach { frame ->
            if (frame.time > 0f) {
                val progress = (1f - frame.time.coerceIn(0f, 1f)).coerceIn(0f, 1f)
                val size = MathUtils.lerp(startSize, endingSize, progress)
                batch.setColor(1f, 1f, 1f, MathUtils.lerp(0f, 0.5f, progress))
                batch.draw(tex, width / 2 - size / 2, height / 2 - size / 2, size, size)
            }
        }

        batch.end()
        batch.projectionMatrix = main.defaultCamera.combined

        frames.forEach {
            it.time -= Gdx.graphics.deltaTime
            if (it.time < 0)
                it.time = 0f
        }
        elapsedTime += Gdx.graphics.deltaTime
    }

    fun reset(extraTime: Float = 0f) {
        elapsedTime = -extraTime
        frames.clear()
        val frameCount = 20
        val duration = 2.75f
        repeat(frameCount) {
            val percent = it.toFloat() / frameCount
            frames += Frame(Interpolation.pow2Out.apply(1f, duration, percent) + extraTime)
        }
        longestTime = (frames.maxBy(Frame::time)?.time ?: 1f) - extraTime
        shortestTime = (frames.minBy(Frame::time)?.time ?: 0f) - extraTime
    }

    override fun renderUpdate() {
        super.renderUpdate()

        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) {
            reset(if (Gdx.input.isShiftDown()) 5f else 0f)
        }
    }

    override fun getDebugString(): String? {
        return "R: Restart\nSHIFT+R: Restart with 3 sec black\n\nframes: ${frames.size}\nelapsed: $elapsedTime\nlongest: $longestTime"
    }

    override fun tickUpdate() {
    }

    override fun dispose() {
    }

}