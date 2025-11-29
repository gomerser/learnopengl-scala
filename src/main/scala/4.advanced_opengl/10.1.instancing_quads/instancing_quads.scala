package learnopengl_4_10_1

import learnopengl.Camera
import learnopengl.CameraMovement.*
import learnopengl.shader_m.Shader
import org.joml.Matrix4f
import org.joml.Vector2f
import org.joml.Vector3f
import org.lwjgl.BufferUtils
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL12.*
import org.lwjgl.opengl.GL13.*
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL30.*
import org.lwjgl.opengl.GL31.*
import org.lwjgl.opengl.GL33.*
import org.lwjgl.stb.STBImage.*
import org.lwjgl.system.Configuration
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil

import java.io.InputStream
import java.nio.ByteBuffer

// settings
val SCR_WIDTH = 800
val SCR_HEIGHT = 600

@main def main(): Unit =

  // glfw: initialize and configure
  // ------------------------------
  if (!glfwInit()) throw new IllegalStateException("Unable to initialize GLFW")
  GLFWErrorCallback.createPrint(System.err).set()
  glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
  glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)
  glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)

  if (System.getProperty("os.name").toLowerCase.contains("mac"))
    glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GL_TRUE)

  // glfw window creation
  // --------------------
  val window = glfwCreateWindow(SCR_WIDTH, SCR_HEIGHT, "LearnOpenGL", 0, 0)
  if (window == 0L) {
    glfwTerminate()
    throw new RuntimeException("Failed to create GLFW window")
  }
  glfwMakeContextCurrent(window)

  // load all OpenGL function pointers for the current context — it’s the LWJGL equivalent of gladLoadGLLoader
  GL.createCapabilities()

  // configure global opengl state
  // -----------------------------
  glEnable(GL_DEPTH_TEST)

  // build and compile shaders
  // -------------------------
  val shader =
    Shader(
      "4.advanced_opengl/10.1.instancing.vs",
      "4.advanced_opengl/10.1.instancing.fs"
    )

// generate a list of 100 quad locations/translation-vectors
  // ---------------------------------------------------------
  val translations = Array.ofDim[Vector2f](100)
  var index = 0
  val offset = 0.1f

  for (y <- -10 until 10 by 2) {
    for (x <- -10 until 10 by 2) {
      val translation = new Vector2f(
        x.toFloat / 10.0f + offset,
        y.toFloat / 10.0f + offset
      )
      translations(index) = translation
      index += 1
    }
  }

  // store instance data in an array buffer
  // --------------------------------------
  val instanceVBO = glGenBuffers();
  glBindBuffer(GL_ARRAY_BUFFER, instanceVBO);
  val translationsBuf = MemoryUtil.memAllocFloat(100 * 2)
  for (t <- translations) translationsBuf.put(t.x).put(t.y)
  translationsBuf.flip()
  glBufferData(GL_ARRAY_BUFFER, translationsBuf, GL_STATIC_DRAW)
  MemoryUtil.memFree(translationsBuf)
  glBindBuffer(GL_ARRAY_BUFFER, 0)

  // set up vertex data (and buffer(s)) and configure vertex attributes
  // ------------------------------------------------------------------
  val quadVertices: Array[Float] = Array(
    // positions     // colors
    -0.05f, 0.05f, 1.0f, 0.0f, 0.0f, 0.05f, -0.05f, 0.0f, 1.0f, 0.0f, -0.05f,
    -0.05f, 0.0f, 0.0f, 1.0f, -0.05f, 0.05f, 1.0f, 0.0f, 0.0f, 0.05f, -0.05f,
    0.0f, 1.0f, 0.0f, 0.05f, 0.05f, 0.0f, 1.0f, 1.0f
  )

  val quadVAO = glGenVertexArrays()
  val quadVBO = glGenBuffers()
  glBindVertexArray(quadVAO)
  glBindBuffer(GL_ARRAY_BUFFER, quadVBO)
  val quadVertexBuf = MemoryUtil.memAllocFloat(quadVertices.length)
  quadVertexBuf.put(quadVertices).flip()
  glBufferData(GL_ARRAY_BUFFER, quadVertexBuf, GL_STATIC_DRAW)
  MemoryUtil.memFree(quadVertexBuf)
  glEnableVertexAttribArray(0)
  glVertexAttribPointer(0, 2, GL_FLOAT, false, 5 * java.lang.Float.BYTES, 0L)
  glEnableVertexAttribArray(1)

  glVertexAttribPointer(
    1,
    3,
    GL_FLOAT,
    false,
    5 * java.lang.Float.BYTES,
    2 * java.lang.Float.BYTES
  )
  // also set instance data
  glEnableVertexAttribArray(2)
  glBindBuffer(
    GL_ARRAY_BUFFER,
    instanceVBO
  ) // this attribute comes from a different vertex buffer
  glVertexAttribPointer(2, 2, GL_FLOAT, false, 2 * java.lang.Float.BYTES, 0L)
  glBindBuffer(GL_ARRAY_BUFFER, 0)
  glVertexAttribDivisor(
    2,
    1
  ) // tell OpenGL this is an instanced vertex attribute.

  // render loop
  // -----------
  while (!glfwWindowShouldClose(window)) {

    // render
    // ------
    glClearColor(0.1f, 0.1f, 0.1f, 1.0f)
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT)

    // draw 100 instanced quads
    shader.use()
    glBindVertexArray(quadVAO)
    glDrawArraysInstanced(
      GL_TRIANGLES,
      0,
      6,
      100
    ) // 100 triangles of 6 vertices each
    glBindVertexArray(0)

    // glfw: swap buffers and poll IO events (keys pressed/released, mouse moved etc.)
    // -------------------------------------------------------------------------------
    glfwSwapBuffers(window)
    glfwPollEvents()
  }

  // optional: de-allocate all resources once they've outlived their purpose:
  // ------------------------------------------------------------------------
  glDeleteVertexArrays(quadVAO)
  glDeleteBuffers(quadVBO)

  glfwTerminate()

// glfw: whenever the window size changed (by OS or user resize) this callback function executes
// ---------------------------------------------------------------------------------------------
def framebuffer_size_callback(window: Long, width: Int, height: Int): Unit = {
  // make sure the viewport matches the new window dimensions; note that width and
  // height will be significantly larger than specified on retina displays.
  glViewport(0, 0, width, height)
}
