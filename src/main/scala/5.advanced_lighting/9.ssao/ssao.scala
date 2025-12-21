package learnopengl_5_9

import learnopengl.Camera
import learnopengl.CameraMovement.*
import learnopengl.model.Model
import learnopengl.shader.Shader
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
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

  // build and compile shaders
  // ------------------------------------
  val shaderGeometryPass =
    Shader(
      "5.advanced_lighting/9.ssao_geometry.vs",
      "5.advanced_lighting/9.ssao_geometry.fs"
    )
  val shaderLightingPass =
    Shader(
      "5.advanced_lighting/9.ssao.vs",
      "5.advanced_lighting/9.ssao_lighting.fs"
    )
  val shaderSSAO =
    Shader(
      "5.advanced_lighting/9.ssao.vs",
      "5.advanced_lighting/9.ssao.fs"
    )
  val shaderSSAOBlur =
    Shader(
      "5.advanced_lighting/9.ssao.vs",
      "5.advanced_lighting/9.ssao_blur.fs"
    )

  // load models
  // -----------
  val backpack = Model("src/main/resources/objects/backpack/backpack.obj")

  // configure g-buffer framebuffer
  // ------------------------------
  val gBuffer = glGenFramebuffers()
  glBindFramebuffer(GL_FRAMEBUFFER, gBuffer)
  // position color buffer
  val gPosition = glGenTextures()
  glBindTexture(GL_TEXTURE_2D, gPosition)
  glTexImage2D(
    GL_TEXTURE_2D,
    0,
    GL_RGBA16F,
    fbWidth,
    fbHeight,
    0,
    GL_RGBA,
    GL_FLOAT,
    0L
  )
  glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
  glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
  glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
  glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
  glFramebufferTexture2D(
    GL_FRAMEBUFFER,
    GL_COLOR_ATTACHMENT0,
    GL_TEXTURE_2D,
    gPosition,
    0
  )
  // normal color buffer
  val gNormal = glGenTextures()
  glBindTexture(GL_TEXTURE_2D, gNormal)
  glTexImage2D(
    GL_TEXTURE_2D,
    0,
    GL_RGBA16F,
    fbWidth,
    fbHeight,
    0,
    GL_RGBA,
    GL_FLOAT,
    0L
  )
  glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
  glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
  glFramebufferTexture2D(
    GL_FRAMEBUFFER,
    GL_COLOR_ATTACHMENT1,
    GL_TEXTURE_2D,
    gNormal,
    0
  )
  // color + specular color buffer
  val gAlbedo = glGenTextures()
  glBindTexture(GL_TEXTURE_2D, gAlbedo)
  glTexImage2D(
    GL_TEXTURE_2D,
    0,
    GL_RGBA,
    fbWidth,
    fbHeight,
    0,
    GL_RGBA,
    GL_UNSIGNED_BYTE,
    0L
  )
  glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
  glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
  glFramebufferTexture2D(
    GL_FRAMEBUFFER,
    GL_COLOR_ATTACHMENT2,
    GL_TEXTURE_2D,
    gAlbedo,
    0
  )
  // tell OpenGL which color attachments we'll use (of this framebuffer) for rendering
  usingStack { stack =>
    val buffers = stack.mallocInt(3)
    buffers.put(GL_COLOR_ATTACHMENT0)
    buffers.put(GL_COLOR_ATTACHMENT1)
    buffers.put(GL_COLOR_ATTACHMENT2)
    buffers.flip()
    glDrawBuffers(buffers)
  }
  // create and attach depth buffer (renderbuffer)
  val rboDepth = glGenRenderbuffers()
  glBindRenderbuffer(GL_RENDERBUFFER, rboDepth)
  glRenderbufferStorage(
    GL_RENDERBUFFER,
    GL_DEPTH_COMPONENT,
    fbWidth,
    fbHeight
  )
  glFramebufferRenderbuffer(
    GL_FRAMEBUFFER,
    GL_DEPTH_ATTACHMENT,
    GL_RENDERBUFFER,
    rboDepth
  )
  // finally check if framebuffer is complete
  if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
    throw new RuntimeException("Framebuffer not complete!")
  glBindFramebuffer(GL_FRAMEBUFFER, 0)

  // also create framebuffer to hold SSAO processing stage
  // -----------------------------------------------------
  val ssaoFBO = glGenFramebuffers()
  val ssaoBlurFBO = glGenFramebuffers()
  glBindFramebuffer(GL_FRAMEBUFFER, ssaoFBO)
  // SSAO color buffer
  val ssaoColorBuffer = glGenTextures()
  glBindTexture(GL_TEXTURE_2D, ssaoColorBuffer)
  glTexImage2D(
    GL_TEXTURE_2D,
    0,
    GL_RED,
    fbWidth,
    fbHeight,
    0,
    GL_RED,
    GL_FLOAT,
    0L
  )
  glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
  glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
  glFramebufferTexture2D(
    GL_FRAMEBUFFER,
    GL_COLOR_ATTACHMENT0,
    GL_TEXTURE_2D,
    ssaoColorBuffer,
    0
  )
  if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
    throw new RuntimeException("SSAO Framebuffer not complete!")
  // and blur stage
  glBindFramebuffer(GL_FRAMEBUFFER, ssaoBlurFBO)
  val ssaoColorBufferBlur = glGenTextures()
  glBindTexture(GL_TEXTURE_2D, ssaoColorBufferBlur)
  glTexImage2D(
    GL_TEXTURE_2D,
    0,
    GL_RED,
    fbWidth,
    fbHeight,
    0,
    GL_RED,
    GL_FLOAT,
    0L
  )
  glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
  glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
  glFramebufferTexture2D(
    GL_FRAMEBUFFER,
    GL_COLOR_ATTACHMENT0,
    GL_TEXTURE_2D,
    ssaoColorBufferBlur,
    0
  )
  if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
    throw new RuntimeException("SSAO Blur Framebuffer not complete!")
  glBindFramebuffer(GL_FRAMEBUFFER, 0)

  // generate sample kernel
  // ----------------------
  val ssaoKernel = ArrayBuffer[Vector3f]()
  for (i <- 0 until 64)
    {
      val sample = Vector3f(
        randomFloat() * 2.0f - 1.0f,
        randomFloat() * 2.0f - 1.0f,
        randomFloat()
      )
        .normalize()
      sample.mul(randomFloat())
      var scale = i.toFloat / 64.0f

      // scale samples s.t. they're more aligned to center of kernel
      scale = ourLerp(0.1f, 1.0f, scale * scale)
      sample.mul(scale)
      ssaoKernel += sample
    }

    // generate noise texture
    // ----------------------
    val ssaoNoise = ArrayBuffer[Vector3f]()
    for (i <- 0 until 16) {
      val noise = Vector3f(
        randomFloat() * 2.0f - 1.0f,
        randomFloat() * 2.0f - 1.0f,
        0.0f
      ) // rotate around z-axis (in tangent space)
      ssaoNoise += noise
    }
    val noiseTexture = glGenTextures()
    glBindTexture(GL_TEXTURE_2D, noiseTexture)
    val noiseBuffer = MemoryUtil.memAllocFloat(ssaoNoise.length * 3)
    ssaoNoise.foreach { v =>
      noiseBuffer.put(v.x).put(v.y).put(v.z)
    }
    noiseBuffer.flip()
    glTexImage2D(
      GL_TEXTURE_2D,
      0,
      GL_RGBA32F,
      4,
      4,
      0,
      GL_RGB,
      GL_FLOAT,
      noiseBuffer
    )
    MemoryUtil.memFree(noiseBuffer)
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT)
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT)

  // lighting info
  // -------------
  // positions
  val lightPos = Vector3f(2.0, 4.0, -2.0)
  val lightColor = Vector3f(0.2, 0.2, 0.7)

  // shader configuration
  // --------------------
  shaderLightingPass.use()
  shaderLightingPass.setInt("gPosition", 0)
  shaderLightingPass.setInt("gNormal", 1)

  // shader configuration
  // --------------------
  shaderLightingPass.use()
  shaderLightingPass.setInt("gPosition", 0)
  shaderLightingPass.setInt("gNormal", 1)
  shaderLightingPass.setInt("gAlbedo", 2)
  shaderLightingPass.setInt("ssao", 3)
  shaderSSAO.use()
  shaderSSAO.setInt("gPosition", 0)
  shaderSSAO.setInt("gNormal", 1)
  shaderSSAO.setInt("texNoise", 2)
  shaderSSAOBlur.use()
  shaderSSAOBlur.setInt("ssaoInput", 0)

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
    glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT)

    // 1. geometry pass: render scene's geometry/color data into gbuffer
    // -----------------------------------------------------------------
    glBindFramebuffer(GL_FRAMEBUFFER, gBuffer)
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT)
    val projection = new Matrix4f()
      .perspective(
        Math.toRadians(camera.zoom).toFloat,
        fbWidth.toFloat / fbHeight.toFloat,
        0.1f,
        50.0f
      )
    val view = camera.getViewMatrix
    shaderGeometryPass.use()
    shaderGeometryPass.setMat4("projection", projection)
    shaderGeometryPass.setMat4("view", view)
    // room cube
    var model = new Matrix4f()
      .translate(Vector3f(0.0, 7.0f, 0.0f))
      .scale(Vector3f(7.5f, 7.5f, 7.5f))
    shaderGeometryPass.setMat4("model", model)
    shaderGeometryPass.setInt(
      "invertedNormals",
      1
    ) // invert normals as we're inside the cube
    renderCube()
    shaderGeometryPass.setInt("invertedNormals", 0)
    // backpack model on the floor
    model = new Matrix4f()
      .translate(Vector3f(0.0f, 0.5f, 0.0))
      .rotate(math.toRadians(-90.0f).toFloat, Vector3f(1.0, 0.0, 0.0))
      .scale(Vector3f(1.0f))
    shaderGeometryPass.setMat4("model", model)
    backpack.draw(shaderGeometryPass)
    glBindFramebuffer(GL_FRAMEBUFFER, 0)

    // 2. generate SSAO texture
    // ------------------------
    glBindFramebuffer(GL_FRAMEBUFFER, ssaoFBO)
    glClear(GL_COLOR_BUFFER_BIT)
    shaderSSAO.use()
    // Send kernel + rotation
    for (i <- 0 until 64)
      shaderSSAO.setVec3(s"samples[$i]", ssaoKernel(i))
    shaderSSAO.setMat4("projection", projection)
    glActiveTexture(GL_TEXTURE0)
    glBindTexture(GL_TEXTURE_2D, gPosition)
    glActiveTexture(GL_TEXTURE1)
    glBindTexture(GL_TEXTURE_2D, gNormal)
    glActiveTexture(GL_TEXTURE2)
    glBindTexture(GL_TEXTURE_2D, noiseTexture)
    renderQuad()
    glBindFramebuffer(GL_FRAMEBUFFER, 0)

    // 3. blur SSAO texture to remove noise
    // ------------------------------------
    glBindFramebuffer(GL_FRAMEBUFFER, ssaoBlurFBO)
    glClear(GL_COLOR_BUFFER_BIT)
    shaderSSAOBlur.use()
    glActiveTexture(GL_TEXTURE0)
    glBindTexture(GL_TEXTURE_2D, ssaoColorBuffer)
    renderQuad()
    glBindFramebuffer(GL_FRAMEBUFFER, 0)

    // 4. lighting pass: traditional deferred Blinn-Phong lighting with added screen-space ambient occlusion
    // -----------------------------------------------------------------------------------------------------
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT)
    shaderLightingPass.use()
    // send light relevant uniforms
    val lightPosView = Vector3f(lightPos).mulPosition(camera.getViewMatrix)
    shaderLightingPass.setVec3("light.Position", lightPosView)
    shaderLightingPass.setVec3("light.Color", lightColor)
    // Update attenuation parameters
    val linear = 0.09f
    val quadratic = 0.032f
    shaderLightingPass.setFloat("light.Linear", linear)
    shaderLightingPass.setFloat("light.Quadratic", quadratic)
    glActiveTexture(GL_TEXTURE0)
    glBindTexture(GL_TEXTURE_2D, gPosition)
    glActiveTexture(GL_TEXTURE1)
    glBindTexture(GL_TEXTURE_2D, gNormal)
    glActiveTexture(GL_TEXTURE2)
    glBindTexture(GL_TEXTURE_2D, gAlbedo)
    glActiveTexture(GL_TEXTURE3) // add extra SSAO texture to lighting pass
    glBindTexture(GL_TEXTURE_2D, ssaoColorBufferBlur)
    renderQuad()

    // glfw: swap buffers and poll IO events (keys pressed/released, mouse moved etc.)
    // -------------------------------------------------------------------------------
    glfwSwapBuffers(window)
    glfwPollEvents()
  }

  glfwTerminate()

// renders a 1x1 quad in NDC with manually calculated tangent vectors
// ------------------------------------------------------------------
var cubeVAO = 0
var cubeVBO = 0
def renderCube() = {
  if (cubeVAO == 0) {

    val vertices = Array(
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
    glBindVertexArray(cubeVAO)
    glBindBuffer(GL_ARRAY_BUFFER, cubeVBO)
    val quadVertexBuf = MemoryUtil.memAllocFloat(vertices.length)
    quadVertexBuf.put(vertices).flip()
    glBufferData(GL_ARRAY_BUFFER, quadVertexBuf, GL_STATIC_DRAW)
    MemoryUtil.memFree(quadVertexBuf)
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
  }
  // render Cube
  glBindVertexArray(cubeVAO)
  glDrawArrays(GL_TRIANGLES, 0, 36)
  glBindVertexArray(0)
}

// renderQuad() renders a 1x1 XY quad in NDC
// -----------------------------------------
var quadVAO = 0
var quadVBO = 0
def renderQuad() = {
  if (quadVAO == 0) {

    val quadVertices = Array(
      // positions        // texture Coords
      -1.0f, 1.0f, 0.0f, 0.0f, 1.0f, -1.0f, -1.0f, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f,
      0.0f, 1.0f, 1.0f, 1.0f, -1.0f, 0.0f, 1.0f, 0.0f
    )
    // setup plane VAO
    quadVAO = glGenVertexArrays()
    quadVBO = glGenBuffers()
    glBindVertexArray(quadVAO)
    glBindBuffer(GL_ARRAY_BUFFER, quadVBO)
    val quadVertexBuf = MemoryUtil.memAllocFloat(quadVertices.length)
    quadVertexBuf.put(quadVertices).flip()
    glBufferData(GL_ARRAY_BUFFER, quadVertexBuf, GL_STATIC_DRAW)
    MemoryUtil.memFree(quadVertexBuf)
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
  }
  glBindVertexArray(quadVAO)
  glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
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

def usingStack[A](f: MemoryStack => A): A =
  val stack = MemoryStack.stackPush()
  try f(stack)
  finally stack.pop()
