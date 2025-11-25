package learnopengl_4_5_2

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
import org.lwjgl.opengl.GL30.*
import org.lwjgl.opengl.GL31.*
import org.lwjgl.opengl.GL33.*
import org.lwjgl.stb.STBImage.*
import org.lwjgl.system.Configuration
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil

import java.io.InputStream
import java.nio.ByteBuffer
import scala.collection.mutable

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
  val shader =
    Shader(
      "4.advanced_opengl/5.2.framebuffers.vs",
      "4.advanced_opengl/5.2.framebuffers.fs"
    )
  val screenShader =
    Shader(
      "4.advanced_opengl/5.2.framebuffers_screen.vs",
      "4.advanced_opengl/5.2.framebuffers_screen.fs"
    )

  // set up vertex data (and buffer(s)) and configure vertex attributes
  // ------------------------------------------------------------------
  val cubeVertices: Array[Float] = Array(
    // positions          // texture Coords
    -0.5f, -0.5f, -0.5f, 0.0f, 0.0f, 0.5f, -0.5f, -0.5f, 1.0f, 0.0f, 0.5f, 0.5f,
    -0.5f, 1.0f, 1.0f, 0.5f, 0.5f, -0.5f, 1.0f, 1.0f, -0.5f, 0.5f, -0.5f, 0.0f,
    1.0f, -0.5f, -0.5f, -0.5f, 0.0f, 0.0f, -0.5f, -0.5f, 0.5f, 0.0f, 0.0f, 0.5f,
    -0.5f, 0.5f, 1.0f, 0.0f, 0.5f, 0.5f, 0.5f, 1.0f, 1.0f, 0.5f, 0.5f, 0.5f,
    1.0f, 1.0f, -0.5f, 0.5f, 0.5f, 0.0f, 1.0f, -0.5f, -0.5f, 0.5f, 0.0f, 0.0f,
    -0.5f, 0.5f, 0.5f, 1.0f, 0.0f, -0.5f, 0.5f, -0.5f, 1.0f, 1.0f, -0.5f, -0.5f,
    -0.5f, 0.0f, 1.0f, -0.5f, -0.5f, -0.5f, 0.0f, 1.0f, -0.5f, -0.5f, 0.5f,
    0.0f, 0.0f, -0.5f, 0.5f, 0.5f, 1.0f, 0.0f, 0.5f, 0.5f, 0.5f, 1.0f, 0.0f,
    0.5f, 0.5f, -0.5f, 1.0f, 1.0f, 0.5f, -0.5f, -0.5f, 0.0f, 1.0f, 0.5f, -0.5f,
    -0.5f, 0.0f, 1.0f, 0.5f, -0.5f, 0.5f, 0.0f, 0.0f, 0.5f, 0.5f, 0.5f, 1.0f,
    0.0f, -0.5f, -0.5f, -0.5f, 0.0f, 1.0f, 0.5f, -0.5f, -0.5f, 1.0f, 1.0f, 0.5f,
    -0.5f, 0.5f, 1.0f, 0.0f, 0.5f, -0.5f, 0.5f, 1.0f, 0.0f, -0.5f, -0.5f, 0.5f,
    0.0f, 0.0f, -0.5f, -0.5f, -0.5f, 0.0f, 1.0f, -0.5f, 0.5f, -0.5f, 0.0f, 1.0f,
    0.5f, 0.5f, -0.5f, 1.0f, 1.0f, 0.5f, 0.5f, 0.5f, 1.0f, 0.0f, 0.5f, 0.5f,
    0.5f, 1.0f, 0.0f, -0.5f, 0.5f, 0.5f, 0.0f, 0.0f, -0.5f, 0.5f, -0.5f, 0.0f,
    1.0f
  )
  val planeVertices = Array[Float](
    // positions          // texture Coords
    5.0f, -0.5f, 5.0f, 2.0f, 0.0f, -5.0f, -0.5f, 5.0f, 0.0f, 0.0f, -5.0f, -0.5f,
    -5.0f, 0.0f, 2.0f, 5.0f, -0.5f, 5.0f, 2.0f, 0.0f, -5.0f, -0.5f, -5.0f, 0.0f,
    2.0f, 5.0f, -0.5f, -5.0f, 2.0f, 2.0f
  )
  val quadVertices =
    Array[Float]( // vertex attributes for a quad that fills the entire screen in Normalized Device Coordinates. NOTE that this plane is now much smaller and at the top of the screen
      // positions   // texCoords
      -0.3f, 1.0f, 0.0f, 1.0f, -0.3f, 0.7f, 0.0f, 0.0f, 0.3f, 0.7f, 1.0f, 0.0f,
      -0.3f, 1.0f, 0.0f, 1.0f, 0.3f, 0.7f, 1.0f, 0.0f, 0.3f, 1.0f, 1.0f, 1.0f)
  // cube VAO
  val cubeVAO = glGenVertexArrays()
  val cubeVBO = glGenBuffers()
  glBindVertexArray(cubeVAO)
  glBindBuffer(GL_ARRAY_BUFFER, cubeVBO)
  val cubeVertexBuf = MemoryUtil.memAllocFloat(cubeVertices.length)
  cubeVertexBuf.put(cubeVertices).flip()
  glBufferData(GL_ARRAY_BUFFER, cubeVertexBuf, GL_STATIC_DRAW)
  MemoryUtil.memFree(cubeVertexBuf)
  glEnableVertexAttribArray(0)
  glVertexAttribPointer(0, 3, GL_FLOAT, false, 5 * java.lang.Float.BYTES, 0L)
  glEnableVertexAttribArray(1)
  glVertexAttribPointer(
    1,
    2,
    GL_FLOAT,
    false,
    5 * java.lang.Float.BYTES,
    3 * java.lang.Float.BYTES
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
  glVertexAttribPointer(0, 3, GL_FLOAT, false, 5 * java.lang.Float.BYTES, 0L)
  glEnableVertexAttribArray(1)
  glVertexAttribPointer(
    1,
    2,
    GL_FLOAT,
    false,
    5 * java.lang.Float.BYTES,
    3L * java.lang.Float.BYTES
  )
  // screen quad VAO
  val quadVAO = glGenVertexArrays()
  val quadVBO = glGenBuffers()
  glBindVertexArray(quadVAO)
  glBindBuffer(GL_ARRAY_BUFFER, quadVBO)
  val quadVerticesBuf = MemoryUtil.memAllocFloat(quadVertices.length)
  quadVerticesBuf.put(quadVertices).flip()
  glBufferData(GL_ARRAY_BUFFER, quadVerticesBuf, GL_STATIC_DRAW)
  MemoryUtil.memFree(quadVerticesBuf)
  glEnableVertexAttribArray(0)
  glVertexAttribPointer(0, 2, GL_FLOAT, false, 4 * java.lang.Float.BYTES, 0L)
  glEnableVertexAttribArray(1)
  glVertexAttribPointer(
    1,
    2,
    GL_FLOAT,
    false,
    4 * java.lang.Float.BYTES,
    2 * java.lang.Float.BYTES
  )

  // load textures
  // -------------
  val cubeTexture = loadTexture("/textures/container.jpg")
  val floorTexture = loadTexture("/textures/metal.png")

  // shader configuration
  // --------------------
  shader.use()
  shader.setInt("texture1", 0)

  screenShader.use()
  screenShader.setInt("screenTexture", 0)

  // framebuffer configuration
  // -------------------------
  val framebuffer = glGenFramebuffers()
  glBindFramebuffer(GL_FRAMEBUFFER, framebuffer)
  // create a color attachment texture
  val textureColorbuffer = glGenTextures()
  glBindTexture(GL_TEXTURE_2D, textureColorbuffer)
  glTexImage2D(
    GL_TEXTURE_2D,
    0,
    GL_RGB,
    SCR_WIDTH,
    SCR_HEIGHT,
    0,
    GL_RGB,
    GL_UNSIGNED_BYTE,
    MemoryUtil.NULL
  )
  glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
  glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
  glFramebufferTexture2D(
    GL_FRAMEBUFFER,
    GL_COLOR_ATTACHMENT0,
    GL_TEXTURE_2D,
    textureColorbuffer,
    0
  )
  // create a renderbuffer object for depth and stencil attachment (we won't be sampling these)
  val rbo = glGenRenderbuffers()
  glBindRenderbuffer(GL_RENDERBUFFER, rbo)
  glRenderbufferStorage(
    GL_RENDERBUFFER,
    GL_DEPTH24_STENCIL8,
    SCR_WIDTH,
    SCR_HEIGHT
  ) // use a single renderbuffer object for both a depth AND stencil buffer.
  glFramebufferRenderbuffer(
    GL_FRAMEBUFFER,
    GL_DEPTH_STENCIL_ATTACHMENT,
    GL_RENDERBUFFER,
    rbo
  ) // now actually attach it
  // now that we actually created the framebuffer and added all attachments we want to check if it is actually complete now
  if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
    throw new RuntimeException(
      "ERROR::FRAMEBUFFER:: Framebuffer is not complete!"
    )
  glBindFramebuffer(GL_FRAMEBUFFER, 0)

  // draw as wireframe
  // glPolygonMode(GL_FRONT_AND_BACK, GL_LINE)

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

    // first render pass: mirror texture.
    // bind to framebuffer and draw to color texture as we normally
    // would, but with the view camera reversed.
    // bind to framebuffer and draw scene as we normally would to color texture
    // ------------------------------------------------------------------------
    glBindFramebuffer(GL_FRAMEBUFFER, framebuffer)
    glEnable(
      GL_DEPTH_TEST
    ) // enable depth testing (is disabled for rendering screen-space quad)

    // make sure we clear the framebuffer's content
    glClearColor(0.1f, 0.1f, 0.1f, 1.0f)
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT)

    shader.use()
    camera.yaw += 180.0f // rotate the camera's yaw 180 degrees around
    camera.processMouseMovement(
      0,
      0,
      false
    ) // call this to make sure it updates its camera vectors, note that we disable pitch constrains for this specific case (otherwise we can't reverse camera's pitch values)
    var view = camera.getViewMatrix
    camera.yaw -= 180.0f // reset it back to its original orientation
    camera.processMouseMovement(0, 0, true)
    val projection = new Matrix4f()
      .perspective(
        Math.toRadians(camera.zoom).toFloat,
        SCR_WIDTH.toFloat / SCR_HEIGHT.toFloat,
        0.1f,
        100.0f
      )
    shader.setMat4("view", view)
    shader.setMat4("projection", projection)
    // cubes
    glBindVertexArray(cubeVAO)
    glActiveTexture(GL_TEXTURE0)
    glBindTexture(GL_TEXTURE_2D, cubeTexture)
    var model = new Matrix4f().translate(-1.0f, 0.0f, -1.0f)
    shader.setMat4("model", model)
    glDrawArrays(GL_TRIANGLES, 0, 36)
    model = new Matrix4f().translate(2.0f, 0.0f, 0.0f)
    shader.setMat4("model", model)
    glDrawArrays(GL_TRIANGLES, 0, 36)
    // floor
    glBindVertexArray(planeVAO)
    glBindTexture(GL_TEXTURE_2D, floorTexture)
    shader.setMat4("model", new Matrix4f())
    glDrawArrays(GL_TRIANGLES, 0, 6)
    glBindVertexArray(0)

    // second render pass: draw as normal
    // ----------------------------------
    glBindFramebuffer(GL_FRAMEBUFFER, 0)

    glClearColor(0.1f, 0.1f, 0.1f, 1.0f)
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT)

    view = camera.getViewMatrix
    shader.setMat4("view", view)

    // cubes
    glBindVertexArray(cubeVAO)
    glActiveTexture(GL_TEXTURE0)
    glBindTexture(GL_TEXTURE_2D, cubeTexture)
    model = new Matrix4f().translate(-1.0f, 0.0f, -1.0f)
    shader.setMat4("model", model)
    glDrawArrays(GL_TRIANGLES, 0, 36)
    model = new Matrix4f().translate(2.0f, 0.0f, 0.0f)
    shader.setMat4("model", model)
    glDrawArrays(GL_TRIANGLES, 0, 36)
    // floor
    glBindVertexArray(planeVAO)
    glBindTexture(GL_TEXTURE_2D, floorTexture)
    shader.setMat4("model", new Matrix4f())
    glDrawArrays(GL_TRIANGLES, 0, 6)
    glBindVertexArray(0)

    // now draw the mirror quad with screen texture
    // --------------------------------------------
    glDisable(
      GL_DEPTH_TEST
    ) // disable depth test so screen-space quad isn't discarded due to depth test.

    screenShader.use()
    glBindVertexArray(quadVAO)
    glBindTexture(
      GL_TEXTURE_2D,
      textureColorbuffer
    ) // use the color attachment texture as the texture of the quad plane
    glDrawArrays(GL_TRIANGLES, 0, 6)

    // glfw: swap buffers and poll IO events (keys pressed/released, mouse moved etc.)
    // -------------------------------------------------------------------------------
    glfwSwapBuffers(window)
    glfwPollEvents()
  }

  // optional: de-allocate all resources once they've outlived their purpose:
  // ------------------------------------------------------------------------
  glDeleteVertexArrays(cubeVAO)
  glDeleteVertexArrays(planeVAO)
  glDeleteVertexArrays(quadVAO)
  glDeleteBuffers(cubeVBO)
  glDeleteBuffers(planeVBO)
  glDeleteBuffers(quadVBO)
  glDeleteRenderbuffers(rbo)
  glDeleteFramebuffers(framebuffer)

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

// utility function for loading a 2D texture from file
// ---------------------------------------------------
def loadTexture(path: String): Int = {
  val textureID = glGenTextures()

  usingStack { stack =>
    val w = stack.mallocInt(1)
    val h = stack.mallocInt(1)
    val nrComponents = stack.mallocInt(1)

    val imgBytes = loadResourceAsTexture(path)
    val data = stbi_load_from_memory(imgBytes, w, h, nrComponents, 0)
    if (data == null)
      throw new RuntimeException("Failed to load texture: " + path)

    val format = nrComponents.get() match {
      case 1 => GL_RED
      case 4 => GL_RGBA
      case 3 => GL_RGB
    }

    glBindTexture(GL_TEXTURE_2D, textureID)
    glTexImage2D(
      GL_TEXTURE_2D,
      0,
      format,
      w.get(),
      h.get(),
      0,
      format,
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
