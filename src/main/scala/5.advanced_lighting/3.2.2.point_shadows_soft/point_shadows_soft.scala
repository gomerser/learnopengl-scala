package learnopengl_5_3_2_2

import learnopengl.Camera
import learnopengl.CameraMovement.*
import learnopengl.shader.Shader
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
import org.lwjgl.opengl.GL32.*
import org.lwjgl.opengl.GL33.*
import org.lwjgl.stb.STBImage.*
import org.lwjgl.system.Configuration
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil

import java.io.InputStream
import java.nio.ByteBuffer
import scala.collection.mutable.ListBuffer

// settings
var SCR_WIDTH = 800
var SCR_HEIGHT = 600
var shadows = true
var shadowsKeyPressed = false

// camera
val camera = Camera(Vector3f(0.0f, 0.0f, 3.0f))
var lastX = SCR_WIDTH / 2.0f
var lastY = SCR_HEIGHT / 2.0f
var firstMouse: Boolean = true

// timing
var deltaTime: Float = 0.0f
var lastFrame: Float = 0.0f

@main def main(): Unit =

  // glfw window creation
  // --------------------
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

  // fetch framebuffer width and height
  val (fbWidth, fbHeight) = usingStack { stack =>
    val xBuf = stack.mallocInt(1)
    val yBuf = stack.mallocInt(1)
    glfwGetFramebufferSize(window, xBuf, yBuf)
    (xBuf.get(0), yBuf.get(0))
  }

  // load all OpenGL function pointers for the current context — it’s the LWJGL equivalent of gladLoadGLLoader
  GL.createCapabilities()

  // configure global opengl state
  // -----------------------------
  glEnable(GL_DEPTH_TEST)
  glEnable(GL_CULL_FACE)

  // build and compile shaders
  // -------------------------
  val shader = Shader(
    "5.advanced_lighting/3.2.2.point_shadows.vs",
    "5.advanced_lighting/3.2.2.point_shadows.fs"
  )
  val simpleDepthShader = Shader(
    "5.advanced_lighting/3.2.2.point_shadows_depth.vs",
    "5.advanced_lighting/3.2.2.point_shadows_depth.fs",
    "5.advanced_lighting/3.2.2.point_shadows_depth.gs"
  )

  // load textures
  // -------------
  val woodTexture = loadTexture("/textures/wood.png")

  // configure depth map FBO
  // -----------------------
  val SHADOW_WIDTH = 1024
  val SHADOW_HEIGHT = 1024
  val depthMapFBO = glGenFramebuffers()
  // create depth cubemap texture
  val depthCubemap = glGenTextures()
  glBindTexture(GL_TEXTURE_CUBE_MAP, depthCubemap)
  for (i <- 0 until 6)
    glTexImage2D(
      GL_TEXTURE_CUBE_MAP_POSITIVE_X + i,
      0,
      GL_DEPTH_COMPONENT,
      SHADOW_WIDTH,
      SHADOW_HEIGHT,
      0,
      GL_DEPTH_COMPONENT,
      GL_FLOAT,
      0L
    )
  glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
  glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
  glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
  glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
  glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE)
  // attach depth texture as FBO's depth buffer
  glBindFramebuffer(GL_FRAMEBUFFER, depthMapFBO)
  glFramebufferTexture(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, depthCubemap, 0)
  glDrawBuffer(GL_NONE)
  glReadBuffer(GL_NONE)
  glBindFramebuffer(GL_FRAMEBUFFER, 0)

  // shader configuration
  // --------------------
  shader.use()
  shader.setInt("diffuseTexture", 0)
  shader.setInt("depthMap", 1)

  // lighting info
  // -------------
  val lightPos = Vector3f(0.0f, 0.0f, 0.0f)

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

    // move light position over time
    lightPos.z = math.sin(glfwGetTime() * 0.5).toFloat * 3.0f

    // render
    // ------
    glClearColor(0.1f, 0.1f, 0.1f, 1.0f)
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT)

    // 0. create depth cubemap transformation matrices
    // -----------------------------------------------
    val near_plane = 1.0f
    val far_plane = 25.0f
    val shadowProj = Matrix4f().identity.perspective(
      Math.toRadians(90.0f).toFloat,
      SHADOW_WIDTH.toFloat / SHADOW_HEIGHT.toFloat,
      near_plane,
      far_plane
    )
    val shadowTransforms = ListBuffer[Matrix4f]()
    shadowTransforms += Matrix4f(shadowProj).mul(
      Matrix4f().lookAt(
        lightPos,
        Vector3f(lightPos).add(Vector3f(1.0f, 0.0f, 0.0f)),
        Vector3f(0.0f, -1.0f, 0.0f)
      )
    )
    shadowTransforms += Matrix4f(shadowProj).mul(
      Matrix4f().lookAt(
        lightPos,
        Vector3f(lightPos).add(Vector3f(-1.0f, 0.0f, 0.0f)),
        Vector3f(0.0f, -1.0f, 0.0f)
      )
    )
    shadowTransforms += Matrix4f(shadowProj).mul(
      Matrix4f().lookAt(
        lightPos,
        Vector3f(lightPos).add(Vector3f(0.0f, 1.0f, 0.0f)),
        Vector3f(0.0f, 0.0f, 1.0f)
      )
    )
    shadowTransforms += Matrix4f(shadowProj).mul(
      Matrix4f().lookAt(
        lightPos,
        Vector3f(lightPos).add(Vector3f(0.0f, -1.0f, 0.0f)),
        Vector3f(0.0f, 0.0f, -1.0f)
      )
    )
    shadowTransforms += Matrix4f(shadowProj).mul(
      Matrix4f().lookAt(
        lightPos,
        Vector3f(lightPos).add(Vector3f(0.0f, 0.0f, 1.0f)),
        Vector3f(0.0f, -1.0f, 0.0f)
      )
    )
    shadowTransforms += Matrix4f(shadowProj).mul(
      Matrix4f().lookAt(
        lightPos,
        Vector3f(lightPos).add(Vector3f(0.0f, 0.0f, -1.0f)),
        Vector3f(0.0f, -1.0f, 0.0f)
      )
    )

    // 1. render scene to depth cubemap
    // --------------------------------
    glViewport(0, 0, SHADOW_WIDTH, SHADOW_HEIGHT)
    glBindFramebuffer(GL_FRAMEBUFFER, depthMapFBO)
    glClear(GL_DEPTH_BUFFER_BIT)
    simpleDepthShader.use();
    for (i <- 0 until 6)
      simpleDepthShader.setMat4(
        "shadowMatrices[" + i + "]",
        shadowTransforms(i)
      )
    simpleDepthShader.setFloat("far_plane", far_plane)
    simpleDepthShader.setVec3("lightPos", lightPos)
    renderScene(simpleDepthShader)
    glBindFramebuffer(GL_FRAMEBUFFER, 0)

    // 2. render scene as normal
    // -------------------------
    glViewport(0, 0, fbWidth, fbHeight)
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT)

    shader.use()
    val projection = Matrix4f()
      .identity()
      .perspective(
        Math.toRadians(camera.zoom).toFloat,
        SCR_WIDTH.toFloat / SCR_HEIGHT.toFloat,
        0.1f,
        100.0f
      )
    val view = camera.getViewMatrix
    shader.setMat4("projection", projection)
    shader.setMat4("view", view)
    // set lighting uniforms
    shader.setVec3("lightPos", lightPos)
    shader.setVec3("viewPos", camera.position)
    shader.setBool(
      "shadows",
      shadows
    ) // enable/disable shadows by pressing 'SPACE'
    shader.setFloat("far_plane", far_plane)
    glActiveTexture(GL_TEXTURE0)
    glBindTexture(GL_TEXTURE_2D, woodTexture)
    glActiveTexture(GL_TEXTURE1)
    glBindTexture(GL_TEXTURE_CUBE_MAP, depthCubemap)
    renderScene(shader)

    // glfw: swap buffers and poll IO events (keys pressed/released, mouse moved etc.)
    // -------------------------------------------------------------------------------
    glfwSwapBuffers(window)
    glfwPollEvents()
  }

  glfwTerminate()

// renders the 3D scene
// --------------------
def renderScene(shader: Shader): Unit =
  // room cube
  val model = Matrix4f().scale(5.0f)
  shader.setMat4("model", model)
  glDisable(
    GL_CULL_FACE
  ) // note that we disable culling here since we render 'inside' the cube instead of the usual 'outside' which throws off the normal culling methods.
  shader.setInt(
    "reverse_normals",
    1
  ) // A small little hack to invert normals when drawing cube from the inside so lighting still works.
  renderCube()
  shader.setInt("reverse_normals", 0) // and of course disable it
  glEnable(GL_CULL_FACE)
  // cubes
  val m1 = Matrix4f()
    .translate(4.0f, -3.5f, 0.0f)
    .scale(0.5f)
  shader.setMat4("model", m1)
  renderCube()
  val m2 = Matrix4f()
    .translate(2.0f, 3.0f, 1.0f)
    .scale(0.75f)
  shader.setMat4("model", m2)
  renderCube()
  val m3 = Matrix4f()
    .translate(-3.0f, -1.0f, 0.0f)
    .scale(0.5f)
  shader.setMat4("model", m3)
  renderCube()
  val m4 = Matrix4f()
    .translate(-1.5f, 1.0f, 1.5f)
    .scale(0.5f)
  shader.setMat4("model", m4)
  renderCube()
  val m5 = Matrix4f()
    .translate(-1.5f, 2.0f, -3.0f)
    .rotate(Math.toRadians(60.0f).toFloat, Vector3f(1.0, 0.0, 1.0).normalize())
    .scale(0.75f)
  shader.setMat4("model", m5)
  renderCube()

// renderCube() renders a 1x1 3D cube in NDC.
// -------------------------------------------------
var cubeVAO = 0
var cubeVBO = 0
def renderCube(): Unit =
  // initialize (if necessary)
  if cubeVAO == 0 then
    val vertices = Array[Float](
      // back face
      -1.0f, -1.0f, -1.0f, 0.0f, 0.0f, -1.0f, 0.0f, 0.0f, // bottom-left
      1.0f, 1.0f, -1.0f, 0.0f, 0.0f, -1.0f, 1.0f, 1.0f, // top-right
      1.0f, -1.0f, -1.0f, 0.0f, 0.0f, -1.0f, 1.0f, 0.0f, // bottom-right
      1.0f, 1.0f, -1.0f, 0.0f, 0.0f, -1.0f, 1.0f, 1.0f, // top-right
      -1.0f, -1.0f, -1.0f, 0.0f, 0.0f, -1.0f, 0.0f, 0.0f, // bottom-left
      -1.0f, 1.0f, -1.0f, 0.0f, 0.0f, -1.0f, 0.0f, 1.0f, // top-left
      // front face
      -1.0f, -1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, // bottom-left
      1.0f, -1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, // bottom-right
      1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, // top-right
      1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, // top-right
      -1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 1.0f, // top-left
      -1.0f, -1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, // bottom-left
      // left face
      -1.0f, 1.0f, 1.0f, -1.0f, 0.0f, 0.0f, 1.0f, 0.0f, // top-right
      -1.0f, 1.0f, -1.0f, -1.0f, 0.0f, 0.0f, 1.0f, 1.0f, // top-left
      -1.0f, -1.0f, -1.0f, -1.0f, 0.0f, 0.0f, 0.0f, 1.0f, // bottom-left
      -1.0f, -1.0f, -1.0f, -1.0f, 0.0f, 0.0f, 0.0f, 1.0f, // bottom-left
      -1.0f, -1.0f, 1.0f, -1.0f, 0.0f, 0.0f, 0.0f, 0.0f, // bottom-right
      -1.0f, 1.0f, 1.0f, -1.0f, 0.0f, 0.0f, 1.0f, 0.0f, // top-right
      // right face
      1.0f, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, // top-left
      1.0f, -1.0f, -1.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f, // bottom-right
      1.0f, 1.0f, -1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, // top-right
      1.0f, -1.0f, -1.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f, // bottom-right
      1.0f, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, // top-left
      1.0f, -1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, // bottom-left
      // bottom face
      -1.0f, -1.0f, -1.0f, 0.0f, -1.0f, 0.0f, 0.0f, 1.0f, // top-right
      1.0f, -1.0f, -1.0f, 0.0f, -1.0f, 0.0f, 1.0f, 1.0f, // top-left
      1.0f, -1.0f, 1.0f, 0.0f, -1.0f, 0.0f, 1.0f, 0.0f, // bottom-left
      1.0f, -1.0f, 1.0f, 0.0f, -1.0f, 0.0f, 1.0f, 0.0f, // bottom-left
      -1.0f, -1.0f, 1.0f, 0.0f, -1.0f, 0.0f, 0.0f, 0.0f, // bottom-right
      -1.0f, -1.0f, -1.0f, 0.0f, -1.0f, 0.0f, 0.0f, 1.0f, // top-right
      // top face
      -1.0f, 1.0f, -1.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, // top-left
      1.0f, 1.0f, 1.0f, 0.0f, 1.0f, 0.0f, 1.0f, 0.0f, // bottom-right
      1.0f, 1.0f, -1.0f, 0.0f, 1.0f, 0.0f, 1.0f, 1.0f, // top-right
      1.0f, 1.0f, 1.0f, 0.0f, 1.0f, 0.0f, 1.0f, 0.0f, // bottom-right
      -1.0f, 1.0f, -1.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, // top-left
      -1.0f, 1.0f, 1.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f // bottom-left
    )

    cubeVAO = glGenVertexArrays()
    cubeVBO = glGenBuffers()
    // fill buffer
    glBindBuffer(GL_ARRAY_BUFFER, cubeVBO)
    val buf = MemoryUtil.memAllocFloat(vertices.length).put(vertices).flip()
    glBufferData(GL_ARRAY_BUFFER, buf, GL_STATIC_DRAW)
    MemoryUtil.memFree(buf)
    // link vertex attributes
    glBindVertexArray(cubeVAO)
    glEnableVertexAttribArray(0)
    glVertexAttribPointer(0, 3, GL_FLOAT, false, 8 * java.lang.Float.BYTES, 0)
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
    glBindBuffer(GL_ARRAY_BUFFER, 0)
    glBindVertexArray(0)

  // render Cube
  glBindVertexArray(cubeVAO)
  glDrawArrays(GL_TRIANGLES, 0, 36)
  glBindVertexArray(0)

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

  if (glfwGetKey(window, GLFW_KEY_SPACE) == GLFW_PRESS && !shadowsKeyPressed)
    shadows = !shadows;
    shadowsKeyPressed = true
  if (glfwGetKey(window, GLFW_KEY_SPACE) == GLFW_RELEASE)
    shadowsKeyPressed = false
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
      throw new RuntimeException("Texture failed to load at path: " + path)

    val format = nrComponents.get() match {
      case 1 => GL_RED
      case 3 => GL_RGB
      case 4 => GL_RGBA
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
    )
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
