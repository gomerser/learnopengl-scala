package learnopengl_4_11_2

import learnopengl.Camera
import learnopengl.CameraMovement.*
import learnopengl.model.Model
import learnopengl.shader.Shader
import org.joml.Matrix4f
import org.joml.Vector3f
import org.lwjgl.BufferUtils
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.opengl.ARBDebugOutput.*
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL12.*
import org.lwjgl.opengl.GL13.*
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL30.*
import org.lwjgl.opengl.GL31.*
import org.lwjgl.opengl.GL32.*
import org.lwjgl.opengl.GL33.*
import org.lwjgl.opengl.GL43.*
import org.lwjgl.opengl.GLDebugMessageARBCallback
import org.lwjgl.opengl.GLUtil
import org.lwjgl.stb.STBImage.*
import org.lwjgl.system.Configuration
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil

import java.io.InputStream
import java.nio.ByteBuffer
import scala.collection.mutable.ArrayBuffer
import scala.util.Random

// settings
val SCR_WIDTH = 800
val SCR_HEIGHT = 600

// camera
val camera = Camera(Vector3f(0.0f, 0.0f, 3.0f))
var lastX = SCR_WIDTH / 2.0f
var lastY = SCR_HEIGHT / 2.0f
var firstMouse: Boolean = true

// timing
var deltaTime: Float = 0.0f
var lastFrame: Float = 0.0f

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
  glfwSetFramebufferSizeCallback(window, framebuffer_size_callback)
  glfwSetCursorPosCallback(window, mouse_callback)
  glfwSetScrollCallback(window, scroll_callback)

  // tell GLFW to capture our mouse
  glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED)

  // load all OpenGL function pointers for the current context — it’s the LWJGL equivalent of gladLoadGLLoader
  GL.createCapabilities()

  // configure global opengl state
  // -----------------------------
  glEnable(GL_DEPTH_TEST)

  // build and compile shaders
  // -------------------------
  val shader = Shader(
    "4.advanced_opengl/11.2.anti_aliasing.vs",
    "4.advanced_opengl/11.2.anti_aliasing.fs"
  )
  val screenShader = Shader(
    "4.advanced_opengl/11.2.aa_post.vs",
    "4.advanced_opengl/11.2.aa_post.fs"
  )

  // set up vertex data (and buffer(s)) and configure vertex attributes
  // ------------------------------------------------------------------
  val cubeVertices: Array[Float] = Array(
    // positions
    -0.5f, -0.5f, -0.5f, 0.5f, -0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f,
    -0.5f, -0.5f, 0.5f, -0.5f, -0.5f, -0.5f, -0.5f, -0.5f, -0.5f, 0.5f, 0.5f,
    -0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f, -0.5f,
    -0.5f, 0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f, -0.5f, -0.5f, -0.5f, -0.5f,
    -0.5f, -0.5f, -0.5f, -0.5f, -0.5f, 0.5f, -0.5f, 0.5f, 0.5f, 0.5f, 0.5f,
    0.5f, 0.5f, 0.5f, -0.5f, 0.5f, -0.5f, -0.5f, 0.5f, -0.5f, -0.5f, 0.5f,
    -0.5f, 0.5f, 0.5f, 0.5f, 0.5f, -0.5f, -0.5f, -0.5f, 0.5f, -0.5f, -0.5f,
    0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f, -0.5f, -0.5f, 0.5f, -0.5f, -0.5f,
    -0.5f, -0.5f, 0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f,
    0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f, -0.5f
  )
  val quadVertices: Array[Float] =
    Array( // vertex attributes for a quad that fills the entire screen in Normalized Device Coordinates.
      // positions   // texCoords
      -1.0f, 1.0f, 0.0f, 1.0f, -1.0f, -1.0f, 0.0f, 0.0f, 1.0f, -1.0f, 1.0f,
      0.0f, -1.0f, 1.0f, 0.0f, 1.0f, 1.0f, -1.0f, 1.0f, 0.0f, 1.0f, 1.0f, 1.0f,
      1.0f)
  // setup cube VAO
  val cubeVAO = glGenVertexArrays()
  val cubeVBO = glGenBuffers()
  glBindVertexArray(cubeVAO)
  glBindBuffer(GL_ARRAY_BUFFER, cubeVBO)
  val cubeVertexBuf = MemoryUtil.memAllocFloat(cubeVertices.length)
  cubeVertexBuf.put(cubeVertices).flip()
  glBufferData(GL_ARRAY_BUFFER, cubeVertexBuf, GL_STATIC_DRAW)
  MemoryUtil.memFree(cubeVertexBuf)
  glEnableVertexAttribArray(0)
  glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * java.lang.Float.BYTES, 0L)
  // setup screen VAO
  val quadVAO = glGenVertexArrays()
  val quadVBO = glGenBuffers()
  glBindVertexArray(quadVAO)
  glBindBuffer(GL_ARRAY_BUFFER, quadVBO)
  val quadVertexBuf = MemoryUtil.memAllocFloat(quadVertices.length)
  quadVertexBuf.put(quadVertices).flip()
  glBufferData(GL_ARRAY_BUFFER, quadVertexBuf, GL_STATIC_DRAW)
  MemoryUtil.memFree(quadVertexBuf)
  glEnableVertexAttribArray(0)
  glVertexAttribPointer(0, 2, GL_FLOAT, false, 4 * java.lang.Float.BYTES, 0L)
  glEnableVertexAttribArray(1)
  glVertexAttribPointer(
    1,
    2,
    GL_FLOAT,
    false,
    4 * java.lang.Float.BYTES,
    2L * java.lang.Float.BYTES
  )

  // configure MSAA framebuffer
  // --------------------------
  val framebuffer = glGenFramebuffers()
  glBindFramebuffer(GL_FRAMEBUFFER, framebuffer)
  // create a multisampled color attachment texture
  val textureColorBufferMultiSampled = glGenTextures()
  glBindTexture(GL_TEXTURE_2D_MULTISAMPLE, textureColorBufferMultiSampled)
  glTexImage2DMultisample(
    GL_TEXTURE_2D_MULTISAMPLE,
    4,
    GL_RGB,
    SCR_WIDTH,
    SCR_HEIGHT,
    true
  )
  glBindTexture(GL_TEXTURE_2D_MULTISAMPLE, 0)
  glFramebufferTexture2D(
    GL_FRAMEBUFFER,
    GL_COLOR_ATTACHMENT0,
    GL_TEXTURE_2D_MULTISAMPLE,
    textureColorBufferMultiSampled,
    0
  )
  // create a (also multisampled) renderbuffer object for depth and stencil attachments
  val rbo = glGenRenderbuffers()
  glBindRenderbuffer(GL_RENDERBUFFER, rbo)
  glRenderbufferStorageMultisample(
    GL_RENDERBUFFER,
    4,
    GL_DEPTH24_STENCIL8,
    SCR_WIDTH,
    SCR_HEIGHT
  )
  glBindRenderbuffer(GL_RENDERBUFFER, 0)
  glFramebufferRenderbuffer(
    GL_FRAMEBUFFER,
    GL_DEPTH_STENCIL_ATTACHMENT,
    GL_RENDERBUFFER,
    rbo
  )

  if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
    throw new RuntimeException(
      "ERROR::FRAMEBUFFER:: Framebuffer is not complete!"
    )
  glBindFramebuffer(GL_FRAMEBUFFER, 0)

  // configure second post-processing framebuffer
  val intermediateFBO = glGenFramebuffers()
  glBindFramebuffer(GL_FRAMEBUFFER, intermediateFBO)
  // create a color attachment texture
  val screenTexture = glGenTextures()
  glBindTexture(GL_TEXTURE_2D, screenTexture)
  glTexImage2D(
    GL_TEXTURE_2D,
    0,
    GL_RGB,
    SCR_WIDTH,
    SCR_HEIGHT,
    0,
    GL_RGB,
    GL_UNSIGNED_BYTE,
    0L
  )
  glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
  glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
  glFramebufferTexture2D(
    GL_FRAMEBUFFER,
    GL_COLOR_ATTACHMENT0,
    GL_TEXTURE_2D,
    screenTexture,
    0
  ) // we only need a color buffer

  if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
    throw new RuntimeException(
      "ERROR::FRAMEBUFFER:: Intermediate framebuffer is not complete!"
    )
  glBindFramebuffer(GL_FRAMEBUFFER, 0)

  // shader configuration
  // --------------------
  screenShader.use()
  screenShader.setInt("screenTexture", 0)

  // render loop
  // -----------
  while (!glfwWindowShouldClose(window))
    {
      // per-frame time logic
      // --------------------
      val currentFrame = glfwGetTime().toFloat
      deltaTime = currentFrame - lastFrame
      lastFrame = currentFrame

      // input
      // -----
      processInput(window)

      // render
      // ------
      glClearColor(0.1f, 0.1f, 0.1f, 1.0f)
      glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT)

      // 1. draw scene as normal in multisampled buffers
      glBindFramebuffer(GL_FRAMEBUFFER, framebuffer)
      glClearColor(0.1f, 0.1f, 0.1f, 1.0f)
      glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT)
      glEnable(GL_DEPTH_TEST)

      // set transformation matrices
      shader.use()
      val projection = new Matrix4f()
        .perspective(
          Math.toRadians(camera.zoom).toFloat,
          SCR_WIDTH.toFloat / SCR_HEIGHT.toFloat,
          0.1f,
          100.0f
        )
      shader.setMat4("projection", projection)
      shader.setMat4("view", camera.getViewMatrix)
      shader.setMat4("model", new Matrix4f().identity)

      glBindVertexArray(cubeVAO)
      glDrawArrays(GL_TRIANGLES, 0, 36)

      // 2. now blit multisampled buffer(s) to normal colorbuffer of intermediate FBO. Image is stored in screenTexture
      glBindFramebuffer(GL_READ_FRAMEBUFFER, framebuffer)
      glBindFramebuffer(GL_DRAW_FRAMEBUFFER, intermediateFBO)
      glBlitFramebuffer(
        0,
        0,
        SCR_WIDTH,
        SCR_HEIGHT,
        0,
        0,
        SCR_WIDTH,
        SCR_HEIGHT,
        GL_COLOR_BUFFER_BIT,
        GL_NEAREST
      )

      // 3. now render quad with scene's visuals as its texture image
      glBindFramebuffer(GL_FRAMEBUFFER, 0)
      glClearColor(1.0f, 1.0f, 1.0f, 1.0f)
      glClear(GL_COLOR_BUFFER_BIT)
      glDisable(GL_DEPTH_TEST)

      // draw Screen quad
      screenShader.use()
      glBindVertexArray(quadVAO)
      glActiveTexture(GL_TEXTURE0)
      glBindTexture(
        GL_TEXTURE_2D,
        screenTexture
      ) // use the now resolved color attachment as the quad's texture
      glDrawArrays(GL_TRIANGLES, 0, 6)

      // glfw: swap buffers and poll IO events (keys pressed/released, mouse moved etc.)
      // -------------------------------------------------------------------------------
      glfwSwapBuffers(window)
      glfwPollEvents()
    }

    glfwTerminate()

// process all input: query GLFW whether relevant keys are pressed/released this frame and react accordingly
// ---------------------------------------------------------------------------------------------------------
def processInput(window: Long): Unit = {
  if glfwGetKey(window, GLFW_KEY_ESCAPE) == GLFW_PRESS then
    glfwSetWindowShouldClose(window, true)

  if glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS then
    camera.processKeyboard(FORWARD, deltaTime)
  if glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS then
    camera.processKeyboard(BACKWARD, deltaTime)
  if glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS then
    camera.processKeyboard(LEFT, deltaTime)
  if glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS then
    camera.processKeyboard(RIGHT, deltaTime)
}

// glfw: whenever the window size changed (by OS or user resize) this callback function executes
// ---------------------------------------------------------------------------------------------
def framebuffer_size_callback(window: Long, width: Int, height: Int): Unit = {
  // make sure the viewport matches the new window dimensions; note that width and
  // height will be significantly larger than specified on retina displays.
  glViewport(0, 0, width, height)
}

// glfw: whenever the mouse moves, this callback is called
// -------------------------------------------------------
def mouse_callback(window: Long, xposIn: Double, yposIn: Double): Unit = {
  val xpos = xposIn.toFloat
  val ypos = yposIn.toFloat

  if (firstMouse) {
    lastX = xpos
    lastY = ypos
    firstMouse = false
  }

  var xoffset = xpos - lastX
  var yoffset =
    lastY - ypos // reversed since y-coordinates go from bottom to top
  lastX = xpos
  lastY = ypos

  camera.processMouseMovement(xoffset, yoffset)
}

// glfw: whenever the mouse scroll wheel scrolls, this callback is called
// ----------------------------------------------------------------------
def scroll_callback(window: Long, xoffset: Double, yoffset: Double): Unit = {
  camera.processMouseScroll(yoffset.toFloat)
}
