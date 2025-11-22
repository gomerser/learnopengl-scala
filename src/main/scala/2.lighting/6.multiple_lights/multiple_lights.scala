package learnopengl_2_6_1

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

// lighting
val lightPos = Vector3f(1.2f, 1.0f, 2.0f)

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

  // load all OpenGL function pointers for the current context — it’s the LWJGL equivalent of gladLoadGLLoader
  GL.createCapabilities()

  // configure global opengl state
  // -----------------------------
  glEnable(GL_DEPTH_TEST)

  // build and compile shaders
  // -------------------------
  val lightingShader = Shader(
    "2.lighting/6.multiple_lights.vs",
    "2.lighting/6.multiple_lights.fs"
  )
  val lightCubeShader =
    Shader("2.lighting/6.light_cube.vs", "2.lighting/6.light_cube.fs")

  // set up vertex data (and buffer(s)) and configure vertex attributes
  // ------------------------------------------------------------------
  val vertices: Array[Float] = Array(
    // positions          // normals           // texture coords
    -0.5f, -0.5f, -0.5f, 0.0f, 0.0f, -1.0f, 0.0f, 0.0f, 0.5f, -0.5f, -0.5f,
    0.0f, 0.0f, -1.0f, 1.0f, 0.0f, 0.5f, 0.5f, -0.5f, 0.0f, 0.0f, -1.0f, 1.0f,
    1.0f, 0.5f, 0.5f, -0.5f, 0.0f, 0.0f, -1.0f, 1.0f, 1.0f, -0.5f, 0.5f, -0.5f,
    0.0f, 0.0f, -1.0f, 0.0f, 1.0f, -0.5f, -0.5f, -0.5f, 0.0f, 0.0f, -1.0f, 0.0f,
    0.0f, -0.5f, -0.5f, 0.5f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.5f, -0.5f, 0.5f,
    0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.5f, 0.5f, 0.5f, 0.0f, 0.0f, 1.0f, 1.0f,
    1.0f, 0.5f, 0.5f, 0.5f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, -0.5f, 0.5f, 0.5f,
    0.0f, 0.0f, 1.0f, 0.0f, 1.0f, -0.5f, -0.5f, 0.5f, 0.0f, 0.0f, 1.0f, 0.0f,
    0.0f, -0.5f, 0.5f, 0.5f, -1.0f, 0.0f, 0.0f, 1.0f, 0.0f, -0.5f, 0.5f, -0.5f,
    -1.0f, 0.0f, 0.0f, 1.0f, 1.0f, -0.5f, -0.5f, -0.5f, -1.0f, 0.0f, 0.0f, 0.0f,
    1.0f, -0.5f, -0.5f, -0.5f, -1.0f, 0.0f, 0.0f, 0.0f, 1.0f, -0.5f, -0.5f,
    0.5f, -1.0f, 0.0f, 0.0f, 0.0f, 0.0f, -0.5f, 0.5f, 0.5f, -1.0f, 0.0f, 0.0f,
    1.0f, 0.0f, 0.5f, 0.5f, 0.5f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.5f, 0.5f,
    -0.5f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.5f, -0.5f, -0.5f, 1.0f, 0.0f, 0.0f,
    0.0f, 1.0f, 0.5f, -0.5f, -0.5f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.5f, -0.5f,
    0.5f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.5f, 0.5f, 0.5f, 1.0f, 0.0f, 0.0f,
    1.0f, 0.0f, -0.5f, -0.5f, -0.5f, 0.0f, -1.0f, 0.0f, 0.0f, 1.0f, 0.5f, -0.5f,
    -0.5f, 0.0f, -1.0f, 0.0f, 1.0f, 1.0f, 0.5f, -0.5f, 0.5f, 0.0f, -1.0f, 0.0f,
    1.0f, 0.0f, 0.5f, -0.5f, 0.5f, 0.0f, -1.0f, 0.0f, 1.0f, 0.0f, -0.5f, -0.5f,
    0.5f, 0.0f, -1.0f, 0.0f, 0.0f, 0.0f, -0.5f, -0.5f, -0.5f, 0.0f, -1.0f, 0.0f,
    0.0f, 1.0f, -0.5f, 0.5f, -0.5f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.5f, 0.5f,
    -0.5f, 0.0f, 1.0f, 0.0f, 1.0f, 1.0f, 0.5f, 0.5f, 0.5f, 0.0f, 1.0f, 0.0f,
    1.0f, 0.0f, 0.5f, 0.5f, 0.5f, 0.0f, 1.0f, 0.0f, 1.0f, 0.0f, -0.5f, 0.5f,
    0.5f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, -0.5f, 0.5f, -0.5f, 0.0f, 1.0f, 0.0f,
    0.0f, 1.0f
  )

  // positions all containers
  val cubePositions = Array[Vector3f](
    Vector3f(0.0f, 0.0f, 0.0f),
    Vector3f(2.0f, 5.0f, -15.0f),
    Vector3f(-1.5f, -2.2f, -2.5f),
    Vector3f(-3.8f, -2.0f, -12.3f),
    Vector3f(2.4f, -0.4f, -3.5f),
    Vector3f(-1.7f, 3.0f, -7.5f),
    Vector3f(1.3f, -2.0f, -2.5f),
    Vector3f(1.5f, 2.0f, -2.5f),
    Vector3f(1.5f, 0.2f, -1.5f),
    Vector3f(-1.3f, 1.0f, -1.5f)
  )

  // positions of the point lights
  val pointLightPositions = Array[Vector3f](
    Vector3f(0.7f, 0.2f, 2.0f),
    Vector3f(2.3f, -3.3f, -4.0f),
    Vector3f(-4.0f, 2.0f, -12.0f),
    Vector3f(0.0f, 0.0f, -3.0f)
  )

  // first, configure the cube's VAO (and VBO)
  val cubeVAO = glGenVertexArrays()
  val VBO = glGenBuffers()

  glBindBuffer(GL_ARRAY_BUFFER, VBO)
  val vertexBuf = MemoryUtil.memAllocFloat(vertices.length)
  vertexBuf.put(vertices).flip()
  glBufferData(GL_ARRAY_BUFFER, vertexBuf, GL_STATIC_DRAW)
  MemoryUtil.memFree(vertexBuf)

  glBindVertexArray(cubeVAO)

  // position attribute
  glVertexAttribPointer(0, 3, GL_FLOAT, false, 8 * java.lang.Float.BYTES, 0L)
  glEnableVertexAttribArray(0)

  // normal attribute
  glVertexAttribPointer(
    1,
    3,
    GL_FLOAT,
    false,
    8 * java.lang.Float.BYTES,
    3 * java.lang.Float.BYTES
  )
  glEnableVertexAttribArray(1)

  glVertexAttribPointer(
    2,
    2,
    GL_FLOAT,
    false,
    8 * java.lang.Float.BYTES,
    6 * java.lang.Float.BYTES
  )
  glEnableVertexAttribArray(2)

  // second, configure the light's VAO (VBO stays the same; the vertices are the same for the light object which is also a 3D cube)
  val lightCubeVAO = glGenVertexArrays()
  glBindVertexArray(lightCubeVAO)

  // we only need to bind to the VBO (to link it with glVertexAttribPointer), no need to fill it; the VBO's data already contains all we need (it's already bound, but we do it again for educational purposes)
  glBindBuffer(GL_ARRAY_BUFFER, VBO)

  // note that we update the lamp's position attribute's stride to reflect the updated buffer data
  glVertexAttribPointer(0, 3, GL_FLOAT, false, 8 * java.lang.Float.BYTES, 0L)
  glEnableVertexAttribArray(0)

  // load textures (we now use a utility function to keep the code more organized)
  // -----------------------------------------------------------------------------
  val diffuseMap = loadTexture("/textures/container2.png")
  val specularMap = loadTexture("/textures/container2_specular.png")

  // shader configuration
  // --------------------
  lightingShader.use()
  lightingShader.setInt("material.diffuse", 0)
  lightingShader.setInt("material.specular", 1)

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

    // be sure to activate shader when setting uniforms/drawing objects
    lightingShader.use()
    lightingShader.setVec3("viewPos", camera.position)
    lightingShader.setFloat("material.shininess", 32.0f)

    /*
        Here we set all the uniforms for the 5/6 types of lights we have. We have to set them manually and index
        the proper PointLight struct in the array to set each uniform variable. This can be done more code-friendly
        by defining light types as classes and set their values in there, or by using a more efficient uniform approach
        by using 'Uniform buffer objects', but that is something we'll discuss in the 'Advanced GLSL' tutorial.
     */
    // directional light
    lightingShader.setVec3("dirLight.direction", -0.2f, -1.0f, -0.3f)
    lightingShader.setVec3("dirLight.ambient", 0.05f, 0.05f, 0.05f)
    lightingShader.setVec3("dirLight.diffuse", 0.4f, 0.4f, 0.4f)
    lightingShader.setVec3("dirLight.specular", 0.5f, 0.5f, 0.5f)
    // point light 1
    lightingShader.setVec3("pointLights[0].position", pointLightPositions(0))
    lightingShader.setVec3("pointLights[0].ambient", 0.05f, 0.05f, 0.05f)
    lightingShader.setVec3("pointLights[0].diffuse", 0.8f, 0.8f, 0.8f)
    lightingShader.setVec3("pointLights[0].specular", 1.0f, 1.0f, 1.0f)
    lightingShader.setFloat("pointLights[0].constant", 1.0f)
    lightingShader.setFloat("pointLights[0].linear", 0.09f)
    lightingShader.setFloat("pointLights[0].quadratic", 0.032f)
    // point light 2
    lightingShader.setVec3("pointLights[1].position", pointLightPositions(1))
    lightingShader.setVec3("pointLights[1].ambient", 0.05f, 0.05f, 0.05f)
    lightingShader.setVec3("pointLights[1].diffuse", 0.8f, 0.8f, 0.8f)
    lightingShader.setVec3("pointLights[1].specular", 1.0f, 1.0f, 1.0f)
    lightingShader.setFloat("pointLights[1].constant", 1.0f)
    lightingShader.setFloat("pointLights[1].linear", 0.09f)
    lightingShader.setFloat("pointLights[1].quadratic", 0.032f)
    // point light 3
    lightingShader.setVec3("pointLights[2].position", pointLightPositions(2))
    lightingShader.setVec3("pointLights[2].ambient", 0.05f, 0.05f, 0.05f)
    lightingShader.setVec3("pointLights[2].diffuse", 0.8f, 0.8f, 0.8f)
    lightingShader.setVec3("pointLights[2].specular", 1.0f, 1.0f, 1.0f)
    lightingShader.setFloat("pointLights[2].constant", 1.0f)
    lightingShader.setFloat("pointLights[2].linear", 0.09f)
    lightingShader.setFloat("pointLights[2].quadratic", 0.032f)
    // point light 4
    lightingShader.setVec3("pointLights[3].position", pointLightPositions(3))
    lightingShader.setVec3("pointLights[3].ambient", 0.05f, 0.05f, 0.05f)
    lightingShader.setVec3("pointLights[3].diffuse", 0.8f, 0.8f, 0.8f)
    lightingShader.setVec3("pointLights[3].specular", 1.0f, 1.0f, 1.0f)
    lightingShader.setFloat("pointLights[3].constant", 1.0f)
    lightingShader.setFloat("pointLights[3].linear", 0.09f)
    lightingShader.setFloat("pointLights[3].quadratic", 0.032f)
    // spotLight
    lightingShader.setVec3("spotLight.position", camera.position)
    lightingShader.setVec3("spotLight.direction", camera.front)
    lightingShader.setVec3("spotLight.ambient", 0.0f, 0.0f, 0.0f)
    lightingShader.setVec3("spotLight.diffuse", 1.0f, 1.0f, 1.0f)
    lightingShader.setVec3("spotLight.specular", 1.0f, 1.0f, 1.0f)
    lightingShader.setFloat("spotLight.constant", 1.0f)
    lightingShader.setFloat("spotLight.linear", 0.09f)
    lightingShader.setFloat("spotLight.quadratic", 0.032f)
    lightingShader.setFloat(
      "spotLight.cutOff",
      math.cos(math.toRadians(12.5f)).toFloat
    )
    lightingShader.setFloat(
      "spotLight.outerCutOff",
      math.cos(math.toRadians(15.0f)).toFloat
    )

    // view/projection transformations
    val projection = new Matrix4f()
      .perspective(
        math.toRadians(camera.zoom).toFloat,
        SCR_WIDTH.toFloat / SCR_HEIGHT.toFloat,
        0.1f,
        100.0f
      )
    val view = camera.getViewMatrix
    lightingShader.setMat4("projection", projection)
    lightingShader.setMat4("view", view)

    // world transformation
    var model = new Matrix4f()
    lightingShader.setMat4("model", model)

    // bind diffuse map
    glActiveTexture(GL_TEXTURE0)
    glBindTexture(GL_TEXTURE_2D, diffuseMap)
    // bind specular map
    glActiveTexture(GL_TEXTURE1)
    glBindTexture(GL_TEXTURE_2D, specularMap)

    // render containers
    glBindVertexArray(cubeVAO)
    for i <- 0 until 10 do {
      // calculate the model matrix for each object and pass it to shader before drawing
      val model =
        Matrix4f()
          .identity()
          .translate(cubePositions(i))
          .rotate(
            math.toRadians(20.0f * i).toFloat,
            1.0f,
            0.3f,
            0.5f
          )
      lightingShader.setMat4("model", model)

      glDrawArrays(GL_TRIANGLES, 0, 36)
    }

    // also draw the lamp object (s)
    lightCubeShader.use()
    lightCubeShader.setMat4("projection", projection)
    lightCubeShader.setMat4("view", view)

    glBindVertexArray(lightCubeVAO)

    for i <- 0 until 4 do {
      model = Matrix4f()
        .translate(pointLightPositions(i))
        .scale(0.2f) // Make it a smaller cube
      lightCubeShader.setMat4("model", model)
      glDrawArrays(GL_TRIANGLES, 0, 36)
    }

    // glfw: swap buffers and poll IO events (keys pressed/released, mouse moved etc.)
    // -------------------------------------------------------------------------------
    glfwSwapBuffers(window)
    glfwPollEvents()
  }

  // optional: de-allocate all resources once they've outlived their purpose:
  // ------------------------------------------------------------------------
  glDeleteVertexArrays(cubeVAO)
  glDeleteVertexArrays(lightCubeVAO)
  glDeleteBuffers(VBO)

  // glfw: terminate, clearing all previously allocated GLFW resources.
  // ------------------------------------------------------------------
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
