package learnopengl_6_2_2_1

import learnopengl.Camera
import learnopengl.CameraMovement.*
import learnopengl.model.Model
import learnopengl.shader.Shader
import org.joml.Matrix3f
import org.joml.Matrix4f
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.BufferUtils
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL12.*
import org.lwjgl.opengl.GL13.*
import org.lwjgl.opengl.GL14.*
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
import scala.collection.immutable.ArraySeq.ofBoolean
import scala.collection.mutable.ArrayBuffer
import scala.util.Random

// settings
val SCR_WIDTH = 800
val SCR_HEIGHT = 600

// camera
val camera = Camera(Vector3f(0.0f, 0.0f, 5.0f))
var lastX = SCR_WIDTH / 2.0f
var lastY = SCR_HEIGHT / 2.0f
var firstMouse: Boolean = true

// timing
var deltaTime: Float = 0.0f
var lastFrame: Float = 0.0f

val random = new Random() // equivalent to default_random_engine
def randomFloat(): Float = random.nextFloat() // [0.0, 1.0)

def ourLerp(a: Float, b: Float, f: Float) = a + f * (b - a)

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
  // set depth function to less than AND equal for skybox depth trick.
  glDepthFunc(GL_LEQUAL)
  // enable seamless cubemap sampling for lower mip levels in the pre-filter map.
  glEnable(GL_TEXTURE_CUBE_MAP_SEAMLESS)

  // build and compile shaders
  // ------------------------------------
  val pbrShader = Shader("6.pbr/2.2.1.pbr.vs", "6.pbr/2.2.1.pbr.fs")
  val equirectangularToCubemapShader = Shader(
    "6.pbr/2.2.1.cubemap.vs",
    "6.pbr/2.2.1.equirectangular_to_cubemap.fs"
  )
  val irradianceShader = Shader(
    "6.pbr/2.2.1.cubemap.vs",
    "6.pbr/2.2.1.irradiance_convolution.fs"
  )
  val prefilterShader =
    Shader("6.pbr/2.2.1.cubemap.vs", "6.pbr/2.2.1.prefilter.fs")
  val brdfShader =
    Shader("6.pbr/2.2.1.brdf.vs", "6.pbr/2.2.1.brdf.fs")
  val backgroundShader =
    Shader("6.pbr/2.2.1.background.vs", "6.pbr/2.2.1.background.fs")

  pbrShader.use()
  pbrShader.setInt("irradianceMap", 0)
  pbrShader.setInt("prefilterMap", 1)
  pbrShader.setInt("brdfLUT", 2)
  pbrShader.setVec3("albedo", 0.5f, 0.0f, 0.0f)
  pbrShader.setFloat("ao", 1.0f)

  backgroundShader.use()
  backgroundShader.setInt("environmentMap", 0)

  // lights
  // ------
  val lightPositions = ArrayBuffer[Vector3f]()
  lightPositions += Vector3f(-10.0f, 10.0f, 10.0f) // back light
  lightPositions += Vector3f(10.0f, 10.0f, 10.0f)
  lightPositions += Vector3f(-10.0f, -10.0f, 10.0f)
  lightPositions += Vector3f(10.0f, -10.0f, 10.0f)
  val lightColors = ArrayBuffer[Vector3f]()
  lightColors += Vector3f(300.0f, 300.0f, 300.0f)
  lightColors += Vector3f(300.0f, 300.0f, 300.0f)
  lightColors += Vector3f(300.0f, 300.0f, 300.0f)
  lightColors += Vector3f(300.0f, 300.0f, 300.0f)
  val nrRows = 7
  val nrColumns = 7
  val spacing = 2.5f

  // pbr: setup framebuffer
  // ----------------------
  val captureFBO = glGenFramebuffers()
  val captureRBO = glGenRenderbuffers()

  glBindFramebuffer(GL_FRAMEBUFFER, captureFBO)
  glBindRenderbuffer(GL_RENDERBUFFER, captureRBO)
  glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24, 512, 512)
  glFramebufferRenderbuffer(
    GL_FRAMEBUFFER,
    GL_DEPTH_ATTACHMENT,
    GL_RENDERBUFFER,
    captureRBO
  )

  // pbr: load the HDR environment map
  // ---------------------------------
  stbi_set_flip_vertically_on_load(true)
  var hdrTexture = 0
  usingStack { stack =>
    val w = stack.mallocInt(1)
    val h = stack.mallocInt(1)
    val nrComponents = stack.mallocInt(1)
    val imgBytes = loadResourceAsTexture("/textures/hdr/newport_loft.hdr")
    val data = stbi_load_from_memory(imgBytes, w, h, nrComponents, 0)
    if data != null then {
      val width = w.get(0)
      val height = h.get(0)
      hdrTexture = glGenTextures()
      glBindTexture(GL_TEXTURE_2D, hdrTexture)
      glTexImage2D(
        GL_TEXTURE_2D,
        0,
        GL_RGB16F,
        width,
        height,
        0,
        GL_RGB,
        GL_FLOAT,
        data
      ) // note how we specify the texture's data value to be float

      glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
      glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
      glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE)
      glTexParameteri(
        GL_TEXTURE_2D,
        GL_TEXTURE_MIN_FILTER,
        GL_LINEAR_MIPMAP_LINEAR
      )
      glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)

      stbi_image_free(data)
    } else {
      println("Failed to load HDR image.")
    }
  }

  // pbr: setup cubemap to render to and attach to framebuffer
  // ---------------------------------------------------------
  val envCubemap = glGenTextures()
  glBindTexture(GL_TEXTURE_CUBE_MAP, envCubemap)
  for (i <- 0 until 6)
    {
      glTexImage2D(
        GL_TEXTURE_CUBE_MAP_POSITIVE_X + i,
        0,
        GL_RGB16F,
        512,
        512,
        0,
        GL_RGB,
        GL_FLOAT,
        0L
      )
    }
    glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
    glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
    glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE)
    glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
    glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MAG_FILTER, GL_LINEAR)

    // pbr: set up projection and view matrices for capturing data onto the 6 cubemap face directions
    // ----------------------------------------------------------------------------------------------
    val captureProjection = new Matrix4f().perspective(
      math.toRadians(90.0f).toFloat,
      1.0f,
      0.1f,
      10.0f
    )
    val captureViews = Array[Matrix4f](
      Matrix4f().lookAt(
        Vector3f(0.0f, 0.0f, 0.0f),
        Vector3f(1.0f, 0.0f, 0.0f),
        Vector3f(0.0f, -1.0f, 0.0f)
      ),
      Matrix4f().lookAt(
        Vector3f(0.0f, 0.0f, 0.0f),
        Vector3f(-1.0f, 0.0f, 0.0f),
        Vector3f(0.0f, -1.0f, 0.0f)
      ),
      Matrix4f().lookAt(
        Vector3f(0.0f, 0.0f, 0.0f),
        Vector3f(0.0f, 1.0f, 0.0f),
        Vector3f(0.0f, 0.0f, 1.0f)
      ),
      Matrix4f().lookAt(
        Vector3f(0.0f, 0.0f, 0.0f),
        Vector3f(0.0f, -1.0f, 0.0f),
        Vector3f(0.0f, 0.0f, -1.0f)
      ),
      Matrix4f().lookAt(
        Vector3f(0.0f, 0.0f, 0.0f),
        Vector3f(0.0f, 0.0f, 1.0f),
        Vector3f(0.0f, -1.0f, 0.0f)
      ),
      Matrix4f().lookAt(
        Vector3f(0.0f, 0.0f, 0.0f),
        Vector3f(0.0f, 0.0f, -1.0f),
        Vector3f(0.0f, -1.0f, 0.0f)
      )
    )

    // pbr: convert HDR equirectangular environment map to cubemap equivalent
    // ----------------------------------------------------------------------
    equirectangularToCubemapShader.use()
    equirectangularToCubemapShader.setInt("equirectangularMap", 0)
    equirectangularToCubemapShader.setMat4("projection", captureProjection)
    glActiveTexture(GL_TEXTURE0)
    glBindTexture(GL_TEXTURE_2D, hdrTexture)

    glViewport(
      0,
      0,
      512,
      512
    ) // don't forget to configure the viewport to the capture dimensions.
    glBindFramebuffer(GL_FRAMEBUFFER, captureFBO)
    for (i <- 0 until 6) {
      equirectangularToCubemapShader.setMat4("view", captureViews(i))
      glFramebufferTexture2D(
        GL_FRAMEBUFFER,
        GL_COLOR_ATTACHMENT0,
        GL_TEXTURE_CUBE_MAP_POSITIVE_X + i,
        envCubemap,
        0
      )
      glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT)

      renderCube()
    }
    glBindFramebuffer(GL_FRAMEBUFFER, 0)

    // then let OpenGL generate mipmaps from first mip face (combatting visible dots artifact)
    glBindTexture(GL_TEXTURE_CUBE_MAP, envCubemap)
    glGenerateMipmap(GL_TEXTURE_CUBE_MAP)

    // pbr: create an irradiance cubemap, and re-scale capture FBO to irradiance scale.
    // --------------------------------------------------------------------------------
    val irradianceMap = glGenTextures()
    glBindTexture(GL_TEXTURE_CUBE_MAP, irradianceMap)
    for (i <- 0 until 6) {
      glTexImage2D(
        GL_TEXTURE_CUBE_MAP_POSITIVE_X + i,
        0,
        GL_RGB16F,
        32,
        32,
        0,
        GL_RGB,
        GL_FLOAT,
        0L
      )
    }
    glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
    glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
    glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE)
    glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
    glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MAG_FILTER, GL_LINEAR)

    glBindFramebuffer(GL_FRAMEBUFFER, captureFBO)
    glBindRenderbuffer(GL_RENDERBUFFER, captureRBO)
    glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24, 32, 32)

    // pbr: solve diffuse integral by convolution to create an irradiance (cube)map.
    // -----------------------------------------------------------------------------
    irradianceShader.use()
    irradianceShader.setInt("environmentMap", 0)
    irradianceShader.setMat4("projection", captureProjection)
    glActiveTexture(GL_TEXTURE0)
    glBindTexture(GL_TEXTURE_CUBE_MAP, envCubemap)

    glViewport(
      0,
      0,
      32,
      32
    ) // don't forget to configure the viewport to the capture dimensions.
    glBindFramebuffer(GL_FRAMEBUFFER, captureFBO)
    for (i <- 0 until 6) {
      irradianceShader.setMat4("view", captureViews(i))
      glFramebufferTexture2D(
        GL_FRAMEBUFFER,
        GL_COLOR_ATTACHMENT0,
        GL_TEXTURE_CUBE_MAP_POSITIVE_X + i,
        irradianceMap,
        0
      )
      glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT)

      renderCube()
    }
    glBindFramebuffer(GL_FRAMEBUFFER, 0)

    // pbr: create a pre-filter cubemap, and re-scale capture FBO to pre-filter scale.
    // --------------------------------------------------------------------------------
    val prefilterMap = glGenTextures()
    glBindTexture(GL_TEXTURE_CUBE_MAP, prefilterMap)
    for (i <- 0 until 6) {
      glTexImage2D(
        GL_TEXTURE_CUBE_MAP_POSITIVE_X + i,
        0,
        GL_RGB16F,
        128,
        128,
        0,
        GL_RGB,
        GL_FLOAT,
        0L
      )
    }
    glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
    glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
    glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE)
    glTexParameteri(
      GL_TEXTURE_CUBE_MAP,
      GL_TEXTURE_MIN_FILTER,
      GL_LINEAR_MIPMAP_LINEAR
    ) // be sure to set minification filter to mip_linear
    glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
    // generate mipmaps for the cubemap so OpenGL automatically allocates the required memory.
    glGenerateMipmap(GL_TEXTURE_CUBE_MAP)

    // pbr: run a quasi monte-carlo simulation on the environment lighting to create a prefilter (cube)map.
    // ----------------------------------------------------------------------------------------------------
    prefilterShader.use()
    prefilterShader.setInt("environmentMap", 0)
    prefilterShader.setMat4("projection", captureProjection)
    glActiveTexture(GL_TEXTURE0)
    glBindTexture(GL_TEXTURE_CUBE_MAP, envCubemap)

    glBindFramebuffer(GL_FRAMEBUFFER, captureFBO)
    val maxMipLevels = 5
    for (mip <- 0 until maxMipLevels) {
      // reisze framebuffer according to mip-level size.
      val mipWidth = (128 * math.pow(0.5, mip)).toInt
      val mipHeight = (128 * math.pow(0.5, mip)).toInt
      glBindRenderbuffer(GL_RENDERBUFFER, captureRBO)
      glRenderbufferStorage(
        GL_RENDERBUFFER,
        GL_DEPTH_COMPONENT24,
        mipWidth,
        mipHeight
      )
      glViewport(0, 0, mipWidth, mipHeight)

      val roughness = mip.toFloat / (maxMipLevels - 1).toFloat
      prefilterShader.setFloat("roughness", roughness)
      for (i <- 0 until 6) {
        prefilterShader.setMat4("view", captureViews(i))
        glFramebufferTexture2D(
          GL_FRAMEBUFFER,
          GL_COLOR_ATTACHMENT0,
          GL_TEXTURE_CUBE_MAP_POSITIVE_X + i,
          prefilterMap,
          mip
        )

        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT)
        renderCube()
      }
    }
    glBindFramebuffer(GL_FRAMEBUFFER, 0)

    // pbr: generate a 2D LUT from the BRDF equations used.
    // ----------------------------------------------------
    val brdfLUTTexture = glGenTextures()

    // pre-allocate enough memory for the LUT texture.
    glBindTexture(GL_TEXTURE_2D, brdfLUTTexture)
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RG16F, 512, 512, 0, GL_RG, GL_FLOAT, 0)
    // be sure to set wrapping mode to GL_CLAMP_TO_EDGE
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)

    // then re-configure capture framebuffer object and render screen-space quad with BRDF shader.
    glBindFramebuffer(GL_FRAMEBUFFER, captureFBO)
    glBindRenderbuffer(GL_RENDERBUFFER, captureRBO)
    glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24, 512, 512)
    glFramebufferTexture2D(
      GL_FRAMEBUFFER,
      GL_COLOR_ATTACHMENT0,
      GL_TEXTURE_2D,
      brdfLUTTexture,
      0
    )

    glViewport(0, 0, 512, 512)
    brdfShader.use()
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT)
    renderQuad()

    glBindFramebuffer(GL_FRAMEBUFFER, 0)

  // initialize static shader uniforms before rendering
  // --------------------------------------------------
  val projection = new Matrix4f().perspective(
    math.toRadians(camera.zoom).toFloat,
    SCR_WIDTH.toFloat / SCR_HEIGHT.toFloat,
    0.1f,
    100.0f
  )
  pbrShader.use()
  pbrShader.setMat4("projection", projection)
  backgroundShader.use()
  backgroundShader.setMat4("projection", projection)

  // then before rendering, configure the viewport to the original framebuffer's screen dimensions
  val (scrWidth, scrHeight) = usingStack { stack =>
    val xBuf = stack.mallocInt(1)
    val yBuf = stack.mallocInt(1)
    glfwGetFramebufferSize(window, xBuf, yBuf)
    (xBuf.get(0), yBuf.get(0))
  }
  glViewport(0, 0, scrWidth, scrHeight)

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
    glClearColor(0.2f, 0.3f, 0.3f, 1.0f)
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT)

    // render scene, supplying the convoluted irradiance map to the final shader.
    // ------------------------------------------------------------------------------------------
    pbrShader.use()
    val view = camera.getViewMatrix
    pbrShader.setMat4("view", view)
    pbrShader.setVec3("camPos", camera.position)

    // bind pre-computed IBL data
    glActiveTexture(GL_TEXTURE0)
    glBindTexture(GL_TEXTURE_CUBE_MAP, irradianceMap)
    glActiveTexture(GL_TEXTURE1)
    glBindTexture(GL_TEXTURE_CUBE_MAP, prefilterMap)
    glActiveTexture(GL_TEXTURE2)
    glBindTexture(GL_TEXTURE_2D, brdfLUTTexture)

    // render rows*column number of spheres with varying metallic/roughness values scaled by rows and columns respectively
    for (row <- 0 until nrRows) {
      pbrShader.setFloat("metallic", row.toFloat / nrRows.toFloat)
      for (col <- 0 until nrColumns) {
        // we clamp the roughness to 0.025 - 1.0 as perfectly smooth surfaces (roughness of 0.0) tend to look a bit off
        // on direct lighting.
        pbrShader.setFloat(
          "roughness",
          math.max(0.05f, math.min(col.toFloat / nrColumns.toFloat, 1.0f))
        )
        val model = new Matrix4f()
          .translate(
            Vector3f(
              (col - (nrColumns / 2)) * spacing,
              (row - (nrRows / 2)) * spacing,
              -2.0f
            )
          )
        pbrShader.setMat4("model", model)
        pbrShader.setMat3("normalMatrix", Matrix3f(model).invert.transpose)
        renderSphere()
      }
    }

    // render light source (simply re-render sphere at light positions)
    // this looks a bit off as we use the same shader, but it'll make their positions obvious and
    // keeps the codeprint small.
    for (i <- 0 until lightPositions.length) {
      val newPos = new Vector3f(lightPositions(i))
        .add(math.sin(glfwGetTime() * 5.0).toFloat * 5.0f, 0.0f, 0.0f)
      // val newPos = lightPositions(i)
      pbrShader.setVec3(s"lightPositions[$i]", newPos)
      pbrShader.setVec3(s"lightColors[$i]", lightColors(i))

      val model = new Matrix4f()
        .translate(newPos)
        .scale(0.5f)
      pbrShader.setMat4("model", model)
      pbrShader.setMat3("normalMatrix", Matrix3f(model).invert.transpose)
      renderSphere()
    }

    // render skybox (render as last to prevent overdraw)
    backgroundShader.use()
    backgroundShader.setMat4("view", view)
    glActiveTexture(GL_TEXTURE0)
    glBindTexture(GL_TEXTURE_CUBE_MAP, envCubemap)
    // glBindTexture(GL_TEXTURE_CUBE_MAP, irradianceMap) // display irradiance map
    // glBindTexture(GL_TEXTURE_CUBE_MAP, prefilterMap) // display prefilter map
    renderCube()

    // render BRDF map to screen
    // brdfShader.Use()
    // renderQuad()

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

// renders (and builds at first invocation) a sphere
// -------------------------------------------------
var sphereVAO = 0
var indexCount = 0
def renderSphere() = {
  if (sphereVAO == 0) {
    sphereVAO = glGenVertexArrays()

    val vbo = glGenBuffers()
    val ebo = glGenBuffers()

    val positions = ArrayBuffer[Vector3f]()
    val uv = ArrayBuffer[Vector2f]()
    val normals = ArrayBuffer[Vector3f]()
    val indices = ArrayBuffer[Int]()

    val X_SEGMENTS = 64
    val Y_SEGMENTS = 64
    val PI = 3.14159265359f
    for (x <- 0 to X_SEGMENTS) {
      for (y <- 0 to Y_SEGMENTS) {
        val xSegment = x.toFloat / X_SEGMENTS.toFloat
        val ySegment = y.toFloat / Y_SEGMENTS.toFloat
        val xPos =
          (math.cos(xSegment * 2.0f * PI) * math.sin(ySegment * PI)).toFloat
        val yPos = math.cos(ySegment * PI).toFloat
        val zPos =
          (math.sin(xSegment * 2.0f * PI) * math.sin(ySegment * PI)).toFloat

        positions += Vector3f(xPos, yPos, zPos)
        uv += Vector2f(xSegment, ySegment)
        normals += Vector3f(xPos, yPos, zPos)
      }
    }

    var oddRow = false
    for (y <- 0 until Y_SEGMENTS) {
      if (!oddRow) // even rows: y == 0, y == 2; and so on
        {
          for (x <- 0 to X_SEGMENTS) {
            indices += y * (X_SEGMENTS + 1) + x
            indices += (y + 1) * (X_SEGMENTS + 1) + x
          }
        } else {
        for (x <- X_SEGMENTS to 0 by -1) {
          indices += (y + 1) * (X_SEGMENTS + 1) + x
          indices += y * (X_SEGMENTS + 1) + x
        }
      }
      oddRow = !oddRow;
    }
    indexCount = indices.length

    val data = ArrayBuffer[Float]()
    for (i <- 0 until positions.length) {
      data += positions(i).x
      data += positions(i).y
      data += positions(i).z
      if (normals.length > 0) {
        data += normals(i).x
        data += normals(i).y
        data += normals(i).z
      }
      if (uv.length > 0) {
        data += uv(i).x
        data += uv(i).y
      }
    }
    glBindVertexArray(sphereVAO)
    glBindBuffer(GL_ARRAY_BUFFER, vbo)
    val dataBuf = MemoryUtil.memAllocFloat(data.length)
    dataBuf.put(data.toArray).flip()
    glBufferData(GL_ARRAY_BUFFER, dataBuf, GL_STATIC_DRAW)
    MemoryUtil.memFree(dataBuf)
    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo)
    val indicesBuf = MemoryUtil.memAllocInt(indices.length)
    indicesBuf.put(indices.toArray).flip()
    glBufferData(GL_ELEMENT_ARRAY_BUFFER, indicesBuf, GL_STATIC_DRAW)
    MemoryUtil.memFree(indicesBuf)
    val stride = (3 + 2 + 3) * java.lang.Float.BYTES
    glEnableVertexAttribArray(0)
    glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0L)
    glEnableVertexAttribArray(1)
    glVertexAttribPointer(
      1,
      3,
      GL_FLOAT,
      false,
      stride,
      3 * java.lang.Float.BYTES
    )
    glEnableVertexAttribArray(2)
    glVertexAttribPointer(
      2,
      2,
      GL_FLOAT,
      false,
      stride,
      6L * java.lang.Float.BYTES
    )
  }

  glBindVertexArray(sphereVAO)
  glDrawElements(GL_TRIANGLE_STRIP, indexCount, GL_UNSIGNED_INT, 0)
}

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

// renderQuad() renders a 1x1 XY quad in NDC
// -----------------------------------------
var quadVAO = 0
var quadVBO = 0
def renderQuad(): Unit =
  if quadVAO == 0 then
    val quadVertices = Array[Float](
      // positions        // texture Coords
      -1.0f, 1.0f, 0.0f, 0.0f, 1.0f, -1.0f, -1.0f, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f,
      0.0f, 1.0f, 1.0f, 1.0f, -1.0f, 0.0f, 1.0f, 0.0f
    )
    // setup plane VAO
    quadVAO = glGenVertexArrays()
    quadVBO = glGenBuffers()
    glBindVertexArray(quadVAO)
    glBindBuffer(GL_ARRAY_BUFFER, quadVBO)
    val buf =
      MemoryUtil.memAllocFloat(quadVertices.length).put(quadVertices).flip()
    glBufferData(GL_ARRAY_BUFFER, buf, GL_STATIC_DRAW)
    MemoryUtil.memFree(buf)
    glEnableVertexAttribArray(0)
    glVertexAttribPointer(0, 3, GL_FLOAT, false, 5 * java.lang.Float.BYTES, 0)
    glEnableVertexAttribArray(1)
    glVertexAttribPointer(
      1,
      2,
      GL_FLOAT,
      false,
      5 * java.lang.Float.BYTES,
      3 * java.lang.Float.BYTES
    )

  glBindVertexArray(quadVAO)
  glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
  glBindVertexArray(0)
