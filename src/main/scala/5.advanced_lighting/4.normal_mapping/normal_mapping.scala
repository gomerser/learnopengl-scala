package learnopengl_5_4

import learnopengl.Camera
import learnopengl.CameraMovement.*
import learnopengl.shader.Shader
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
  // ------------------------------------
  val shader =
    Shader(
      "5.advanced_lighting/4.normal_mapping.vs",
      "5.advanced_lighting/4.normal_mapping.fs"
    )

    // load textures
    // -------------
  val diffuseMap = loadTexture("/textures/brickwall.jpg")
  val normalMap = loadTexture("/textures/brickwall_normal.jpg")

  // shader configuration
  // --------------------
  shader.use()
  shader.setInt("diffuseMap", 0)
  shader.setInt("normalMap", 1)

  // lighting info
  // -------------
  val lightPos = Vector3f(0.5f, 1.0f, 0.3f)

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

    // configure view/projection matrices
    val projection = new Matrix4f()
      .perspective(
        Math.toRadians(camera.zoom).toFloat,
        SCR_WIDTH.toFloat / SCR_HEIGHT.toFloat,
        0.1f,
        100.0f
      )
    val view = camera.getViewMatrix
    shader.use()
    shader.setMat4("projection", projection)
    shader.setMat4("view", view)
    // render normal-mapped quad
    var model = new Matrix4f().rotate(
      Math.toRadians(glfwGetTime() * -10.0f).toFloat,
      Vector3f(1.0, 0.0, 1.0).normalize()
    ) // rotate the quad to show normal mapping from multiple directions
    shader.setMat4("model", model)
    shader.setVec3("viewPos", camera.position)
    shader.setVec3("lightPos", lightPos)
    glActiveTexture(GL_TEXTURE0)
    glBindTexture(GL_TEXTURE_2D, diffuseMap)
    glActiveTexture(GL_TEXTURE1)
    glBindTexture(GL_TEXTURE_2D, normalMap)
    renderQuad()

    // model = new Matrix4f()
    //   .translate(lightPos)
    //   .scale(0.1f)
    // shader.setMat4("model", model)
    // renderQuad()

    // glfw: swap buffers and poll IO events (keys pressed/released, mouse moved etc.)
    // -------------------------------------------------------------------------------
    glfwSwapBuffers(window)
    glfwPollEvents()
  }

  glfwTerminate()

// renders a 1x1 quad in NDC with manually calculated tangent vectors
// ------------------------------------------------------------------
var quadVAO = 0
var quadVBO = 0
def renderQuad() = {
  if (quadVAO == 0) {
    // positions
    val pos1 = Vector3f(-1.0f, 1.0f, 0.0f)
    val pos2 = Vector3f(-1.0f, -1.0f, 0.0f)
    val pos3 = Vector3f(1.0f, -1.0f, 0.0f)
    val pos4 = Vector3f(1.0f, 1.0f, 0.0f)
    // texture coordinates
    val uv1 = Vector2f(0.0f, 1.0f)
    val uv2 = Vector2f(0.0f, 0.0f)
    val uv3 = Vector2f(1.0f, 0.0f)
    val uv4 = Vector2f(1.0f, 1.0f)
    // normal vector
    val nm = Vector3f(0.0f, 0.0f, 1.0f)

    // calculate tangent/bitangent vectors of both triangles
    val tangent1 = Vector3f()
    val bitangent1 = Vector3f()
    val tangent2 = Vector3f()
    val bitangent2 = Vector3f()
    // triangle 1
    // ----------
    var edge1 = Vector3f(pos2).sub(pos1)
    var edge2 = Vector3f(pos3).sub(pos1)
    var deltaUV1 = Vector2f(uv2).sub(uv1)
    var deltaUV2 = Vector2f(uv3).sub(uv1)

    var f = 1.0f / (deltaUV1.x * deltaUV2.y - deltaUV2.x * deltaUV1.y)

    tangent1.x = f * (deltaUV2.y * edge1.x - deltaUV1.y * edge2.x)
    tangent1.y = f * (deltaUV2.y * edge1.y - deltaUV1.y * edge2.y)
    tangent1.z = f * (deltaUV2.y * edge1.z - deltaUV1.y * edge2.z)

    bitangent1.x = f * (-deltaUV2.x * edge1.x + deltaUV1.x * edge2.x)
    bitangent1.y = f * (-deltaUV2.x * edge1.y + deltaUV1.x * edge2.y)
    bitangent1.z = f * (-deltaUV2.x * edge1.z + deltaUV1.x * edge2.z)

    // triangle 2
    // ----------
    edge1 = Vector3f(pos3).sub(pos1)
    edge2 = Vector3f(pos4).sub(pos1)
    deltaUV1 = Vector2f(uv3).sub(uv1)
    deltaUV2 = Vector2f(uv4).sub(uv1)

    f = 1.0f / (deltaUV1.x * deltaUV2.y - deltaUV2.x * deltaUV1.y)

    tangent2.x = f * (deltaUV2.y * edge1.x - deltaUV1.y * edge2.x)
    tangent2.y = f * (deltaUV2.y * edge1.y - deltaUV1.y * edge2.y)
    tangent2.z = f * (deltaUV2.y * edge1.z - deltaUV1.y * edge2.z)

    bitangent2.x = f * (-deltaUV2.x * edge1.x + deltaUV1.x * edge2.x)
    bitangent2.y = f * (-deltaUV2.x * edge1.y + deltaUV1.x * edge2.y)
    bitangent2.z = f * (-deltaUV2.x * edge1.z + deltaUV1.x * edge2.z)

    val quadVertices = Array(
      // positions            // normal         // texcoords  // tangent                          // bitangent
      pos1.x,
      pos1.y,
      pos1.z,
      nm.x,
      nm.y,
      nm.z,
      uv1.x,
      uv1.y,
      tangent1.x,
      tangent1.y,
      tangent1.z,
      bitangent1.x,
      bitangent1.y,
      bitangent1.z,
      pos2.x,
      pos2.y,
      pos2.z,
      nm.x,
      nm.y,
      nm.z,
      uv2.x,
      uv2.y,
      tangent1.x,
      tangent1.y,
      tangent1.z,
      bitangent1.x,
      bitangent1.y,
      bitangent1.z,
      pos3.x,
      pos3.y,
      pos3.z,
      nm.x,
      nm.y,
      nm.z,
      uv3.x,
      uv3.y,
      tangent1.x,
      tangent1.y,
      tangent1.z,
      bitangent1.x,
      bitangent1.y,
      bitangent1.z,
      pos1.x,
      pos1.y,
      pos1.z,
      nm.x,
      nm.y,
      nm.z,
      uv1.x,
      uv1.y,
      tangent2.x,
      tangent2.y,
      tangent2.z,
      bitangent2.x,
      bitangent2.y,
      bitangent2.z,
      pos3.x,
      pos3.y,
      pos3.z,
      nm.x,
      nm.y,
      nm.z,
      uv3.x,
      uv3.y,
      tangent2.x,
      tangent2.y,
      tangent2.z,
      bitangent2.x,
      bitangent2.y,
      bitangent2.z,
      pos4.x,
      pos4.y,
      pos4.z,
      nm.x,
      nm.y,
      nm.z,
      uv4.x,
      uv4.y,
      tangent2.x,
      tangent2.y,
      tangent2.z,
      bitangent2.x,
      bitangent2.y,
      bitangent2.z
    )
    // configure plane VAO
    quadVAO = glGenVertexArrays()
    quadVBO = glGenBuffers()
    glBindVertexArray(quadVAO)
    glBindBuffer(GL_ARRAY_BUFFER, quadVBO)
    val quadVertexBuf = MemoryUtil.memAllocFloat(quadVertices.length)
    quadVertexBuf.put(quadVertices).flip()
    glBufferData(GL_ARRAY_BUFFER, quadVertexBuf, GL_STATIC_DRAW)
    MemoryUtil.memFree(quadVertexBuf)
    glEnableVertexAttribArray(0)
    glVertexAttribPointer(0, 3, GL_FLOAT, false, 14 * java.lang.Float.BYTES, 0)
    glEnableVertexAttribArray(1)
    glVertexAttribPointer(
      1,
      3,
      GL_FLOAT,
      false,
      14 * java.lang.Float.BYTES,
      3 * java.lang.Float.BYTES
    )
    glEnableVertexAttribArray(2)
    glVertexAttribPointer(
      2,
      2,
      GL_FLOAT,
      false,
      14 * java.lang.Float.BYTES,
      6 * java.lang.Float.BYTES
    )
    glEnableVertexAttribArray(3)
    glVertexAttribPointer(
      3,
      3,
      GL_FLOAT,
      false,
      14 * java.lang.Float.BYTES,
      8 * java.lang.Float.BYTES
    )
    glEnableVertexAttribArray(4)
    glVertexAttribPointer(
      4,
      3,
      GL_FLOAT,
      false,
      14 * java.lang.Float.BYTES,
      11 * java.lang.Float.BYTES
    )
  }
  glBindVertexArray(quadVAO)
  glDrawArrays(GL_TRIANGLES, 0, 6)
  glBindVertexArray(0)
}

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

    glTexParameteri(
      GL_TEXTURE_2D,
      GL_TEXTURE_WRAP_S,
      if (format == GL_RGBA) GL_CLAMP_TO_EDGE else GL_REPEAT
    ) // for this tutorial: use GL_CLAMP_TO_EDGE to prevent semi-transparent borders. Due to interpolation it takes texels from next repeat
    glTexParameteri(
      GL_TEXTURE_2D,
      GL_TEXTURE_WRAP_T,
      if (format == GL_RGBA) GL_CLAMP_TO_EDGE else GL_REPEAT
    )
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
