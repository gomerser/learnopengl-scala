package learnopengl_5_2

import learnopengl.Camera
import learnopengl.CameraMovement.*
import learnopengl.shader_m.Shader
import org.joml.Matrix4f
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
import org.lwjgl.opengl.GL21.*
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
var gammaEnabled = false
var gammaKeyPressed = false

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
  glEnable(GL_BLEND)
  glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

  // build and compile shaders
  // -------------------------
  val shader =
    Shader(
      "5.advanced_lighting/2.gamma_correction.vs",
      "5.advanced_lighting/2.gamma_correction.fs"
    )

  // set up vertex data (and buffer(s)) and configure vertex attributes
  // ------------------------------------------------------------------
  val planeVertices = Array[Float](
    // positions            // normals         // texcoords
    10.0f, -0.5f, 10.0f, 0.0f, 1.0f, 0.0f, 10.0f, 0.0f, -10.0f, -0.5f, 10.0f,
    0.0f, 1.0f, 0.0f, 0.0f, 0.0f, -10.0f, -0.5f, -10.0f, 0.0f, 1.0f, 0.0f, 0.0f,
    10.0f, 10.0f, -0.5f, 10.0f, 0.0f, 1.0f, 0.0f, 10.0f, 0.0f, -10.0f, -0.5f,
    -10.0f, 0.0f, 1.0f, 0.0f, 0.0f, 10.0f, 10.0f, -0.5f, -10.0f, 0.0f, 1.0f,
    0.0f, 10.0f, 10.0f
  )
  // plane VAO
  val planeVAO = glGenVertexArrays()
  val planeVBO = glGenBuffers()
  glBindVertexArray(planeVAO)
  glBindBuffer(GL_ARRAY_BUFFER, planeVBO)
  val planeVertexBuf = MemoryUtil.memAllocFloat(planeVertices.length)
  planeVertexBuf.put(planeVertices).flip()
  glBufferData(GL_ARRAY_BUFFER, planeVertexBuf, GL_STATIC_DRAW)
  MemoryUtil.memFree(planeVertexBuf)
  glEnableVertexAttribArray(0)
  glVertexAttribPointer(0, 3, GL_FLOAT, false, 8 * java.lang.Float.BYTES, 0L)
  glEnableVertexAttribArray(1)
  glVertexAttribPointer(
    1,
    3,
    GL_FLOAT,
    false,
    8 * java.lang.Float.BYTES,
    3 * java.lang.Float.BYTES
  )
  glEnableVertexAttribArray(2)
  glVertexAttribPointer(
    2,
    2,
    GL_FLOAT,
    false,
    8 * java.lang.Float.BYTES,
    6 * java.lang.Float.BYTES
  )
  glBindVertexArray(0)

  // load textures
  // -------------
  val floorTexture = loadTexture("/textures/wood.png", false)
  val floorTextureGammaCorrected = loadTexture("/textures/wood.png", true)

  // shader configuration
  // --------------------
  shader.use();
  shader.setInt("floorTexture", 0)

  // lighting info
  // -------------
  val lightPos = Vector3f(0.0f, 0.0f, 0.0f)
  val lightPositions = Array[Vector3f](
    Vector3f(-3.0f, 0.0f, 0.0f),
    Vector3f(-1.0f, 0.0f, 0.0f),
    Vector3f(1.0f, 0.0f, 0.0f),
    Vector3f(3.0f, 0.0f, 0.0f)
  )
  val lightColors = Array[Vector3f](
    Vector3f(0.25f),
    Vector3f(0.50f),
    Vector3f(0.75f),
    Vector3f(1.00f)
  )

  // render loop
  // -----------
  while (!glfwWindowShouldClose(window)) {

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

    // draw objects
    shader.use()
    val projection = new Matrix4f()
      .perspective(
        Math.toRadians(camera.zoom).toFloat,
        SCR_WIDTH.toFloat / SCR_HEIGHT.toFloat,
        0.1f,
        100.0f
      )
    val view = camera.getViewMatrix
    shader.setMat4("projection", projection);
    shader.setMat4("view", view)
    // set light uniforms
    val positionBuf = BufferUtils.createFloatBuffer(lightPositions.length * 3)
    lightPositions.foreach(v => positionBuf.put(v.x).put(v.y).put(v.z))
    positionBuf.flip()
    glUniform3fv(glGetUniformLocation(shader.ID, "lightPositions"), positionBuf)
    val colorBuf = BufferUtils.createFloatBuffer(lightColors.length * 3)
    lightColors.foreach(v => colorBuf.put(v.x).put(v.y).put(v.z))
    colorBuf.flip()
    glUniform3fv(glGetUniformLocation(shader.ID, "lightColors"), colorBuf)
    shader.setVec3("viewPos", camera.position)
    shader.setBool("gamma", gammaEnabled)
    // floor
    glBindVertexArray(planeVAO)
    glActiveTexture(GL_TEXTURE0)
    glBindTexture(
      GL_TEXTURE_2D,
      if (gammaEnabled) floorTextureGammaCorrected else floorTexture
    )
    glDrawArrays(GL_TRIANGLES, 0, 6)

    println(if (gammaEnabled) "Gamma enabled" else "Gamma disabled")

    // glfw: swap buffers and poll IO events (keys pressed/released, mouse moved etc.)
    // -------------------------------------------------------------------------------
    glfwSwapBuffers(window)
    glfwPollEvents()
  }

  // optional: de-allocate all resources once they've outlived their purpose:
  // ------------------------------------------------------------------------
  glDeleteVertexArrays(planeVAO)
  glDeleteBuffers(planeVBO)

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

  if (glfwGetKey(window, GLFW_KEY_SPACE) == GLFW_PRESS && !gammaKeyPressed) {
    gammaEnabled = !gammaEnabled
    gammaKeyPressed = true
  }
  if (glfwGetKey(window, GLFW_KEY_SPACE) == GLFW_RELEASE) {
    gammaKeyPressed = false
  }
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

// utility function for loading a 2D texture from file
// ---------------------------------------------------
def loadTexture(path: String, gammaCorrection: Boolean): Int = {
  val textureID = glGenTextures()

  usingStack { stack =>
    val w = stack.mallocInt(1)
    val h = stack.mallocInt(1)
    val nrComponents = stack.mallocInt(1)

    val imgBytes = loadResourceAsTexture(path)
    val data = stbi_load_from_memory(imgBytes, w, h, nrComponents, 0)
    if (data == null)
      throw new RuntimeException("Failed to load texture: " + path)

    val (internalFormat, dataFormat) = nrComponents.get() match {
      case 1 => (GL_RED, GL_RED)
      case 3 => (if (gammaCorrection) GL_SRGB else GL_RGB, GL_RGB)
      case 4 => (if (gammaCorrection) GL_SRGB_ALPHA else GL_RGBA, GL_RGBA)
    }

    glBindTexture(GL_TEXTURE_2D, textureID)
    glTexImage2D(
      GL_TEXTURE_2D,
      0,
      internalFormat,
      w.get(),
      h.get(),
      0,
      dataFormat,
      GL_UNSIGNED_BYTE,
      data
    )
    glGenerateMipmap(GL_TEXTURE_2D)

    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT)
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT)
    glTexParameteri(
      GL_TEXTURE_2D,
      GL_TEXTURE_MIN_FILTER,
      GL_LINEAR_MIPMAP_LINEAR
    )
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)

    stbi_image_free(data)
  }

  textureID
}

def usingStack[A](f: MemoryStack => A): A =
  val stack = MemoryStack.stackPush()
  try f(stack)
  finally stack.pop()

def loadResourceAsTexture(name: String): ByteBuffer =
  val stream = this.getClass.getResourceAsStream(name)
  if stream == null then
    throw new RuntimeException("Resource not found: " + name)

  val bytes = stream.readAllBytes()
  val buffer = BufferUtils.createByteBuffer(bytes.length)
  buffer.put(bytes)
  buffer.flip()
  buffer
