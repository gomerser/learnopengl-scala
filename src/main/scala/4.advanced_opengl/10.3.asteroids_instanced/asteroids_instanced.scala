package learnopengl_4_10_3

import learnopengl.Camera
import learnopengl.CameraMovement.*
import learnopengl.model.Model
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
import org.lwjgl.opengl.GL30.*
import org.lwjgl.opengl.GL31.*
import org.lwjgl.opengl.GL33.*
import org.lwjgl.stb.STBImage.*
import org.lwjgl.system.Configuration
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil

import java.io.InputStream
import java.nio.ByteBuffer
import scala.util.Random
import scala.collection.mutable.ArrayBuffer

import org.lwjgl.opengl.GL43.*
import org.lwjgl.opengl.ARBDebugOutput._
import org.lwjgl.opengl.GLDebugMessageARBCallback
import org.lwjgl.opengl.GLUtil

// settings
val SCR_WIDTH = 800
val SCR_HEIGHT = 600

// camera
val camera = Camera(Vector3f(0.0f, 0.0f, 155.0f))
var lastX = SCR_WIDTH / 2.0f
var lastY = SCR_HEIGHT / 2.0f
var firstMouse: Boolean = true

// timing
var deltaTime: Float = 0.0f
var lastFrame: Float = 0.0f

val sizeofMatrix4f = 16 * java.lang.Float.BYTES // 64 bytes
val sizeofVector4f = 4 * java.lang.Float.BYTES // 16 bytes

@main def main(): Unit =

  // glfw: initialize and configure
  // ------------------------------
  if (!glfwInit()) throw new IllegalStateException("Unable to initialize GLFW")
  GLFWErrorCallback.createPrint(System.err).set()
  glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
  glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)
  glfwWindowHint(GLFW_OPENGL_DEBUG_CONTEXT, GL_TRUE)
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
  val asteroidShader =
    Shader(
      "4.advanced_opengl/10.3.asteroids.vs",
      "4.advanced_opengl/10.3.asteroids.fs"
    )
  val planetShader =
    Shader(
      "4.advanced_opengl/10.3.planet.vs",
      "4.advanced_opengl/10.3.planet.fs"
    )

  // load models
  // -----------
  val rock = Model("src/main/resources/objects/rock/rock.obj")
  val planet = Model("src/main/resources/objects/planet/planet.obj")

  // generate a large list of semi-random model transformation matrices
  // ------------------------------------------------------------------

  val amount = 100000
  val modelMatrices = ArrayBuffer[Matrix4f]()
  val rng = new Random(glfwGetTime().toLong) // initialize random seed
  val radius = 150.0f
  val offset = 25.0f
  for (i <- 0 until amount) {
    val model = new Matrix4f().identity
    // 1. translation: displace along circle with 'radius' in range [-offset, offset]
    val angle = i.toFloat / amount * 360.0f
    var displacement = (rng.nextInt(2 * offset.toInt * 100)) / 100.0f - offset
    val x = math.sin(angle).toFloat * radius + displacement
    displacement = (rng.nextInt(2 * offset.toInt * 100)) / 100.0f - offset
    val y =
      displacement * 0.4f; // keep height of asteroid field smaller compared to width of x and z
    displacement = (rng.nextInt(2 * offset.toInt * 100)) / 100.0f - offset
    val z = math.cos(angle).toFloat * radius + displacement
    model.translate(x, y, z)

    // 2. scale: Scale between 0.05 and 0.25f
    val scale = rng.nextInt(20).toFloat / 100.0 + 0.05
    model.scale(scale.toFloat)

    // 3. rotation: add random rotation around a (semi)randomly picked rotation axis vector
    val rotAngle = rng.nextInt(360).toFloat
    model.rotate(rotAngle, 0.4f, 0.6f, 0.8f)

    // 4. now add to list of matrices
    modelMatrices.addOne(model)
  }

  // configure instanced array
  // -------------------------
  val buffer = glGenBuffers()
  glBindBuffer(GL_ARRAY_BUFFER, buffer);
  val modelBuf = MemoryUtil.memAllocFloat(modelMatrices.length * 16)
  modelMatrices.foreach { modelMatrix =>
    modelMatrix.get(modelBuf)
    modelBuf.position(modelBuf.position() + 16)
  }
  modelBuf.flip()
  glBufferData(GL_ARRAY_BUFFER, modelBuf, GL_STATIC_DRAW)
  MemoryUtil.memFree(modelBuf)

  // set transformation matrices as an instance vertex attribute (with divisor 1)
  // note: we're cheating a little by taking the, now publicly declared, VAO of the model's mesh(es) and adding new vertexAttribPointers
  // normally you'd want to do this in a more organized fashion, but for learning purposes this will do.
  // -----------------------------------------------------------------------------------------------------------------------------------
  for (i <- 0 until rock.meshes.size) {
    val vao = rock.meshes(i).vao
    glBindVertexArray(vao)
    // set attribute pointers for matrix (4 times vec4)
    glEnableVertexAttribArray(3)
    glVertexAttribPointer(3, 4, GL_FLOAT, false, sizeofMatrix4f, 0)
    glEnableVertexAttribArray(4)
    glVertexAttribPointer(4, 4, GL_FLOAT, false, sizeofMatrix4f, sizeofVector4f)
    glEnableVertexAttribArray(5)
    glVertexAttribPointer(
      5,
      4,
      GL_FLOAT,
      false,
      sizeofMatrix4f,
      2 * sizeofVector4f
    )
    glEnableVertexAttribArray(6)
    glVertexAttribPointer(
      6,
      4,
      GL_FLOAT,
      false,
      sizeofMatrix4f,
      3 * sizeofVector4f
    )

    glVertexAttribDivisor(3, 1)
    glVertexAttribDivisor(4, 1)
    glVertexAttribDivisor(5, 1)
    glVertexAttribDivisor(6, 1)

    glBindVertexArray(0)
  }

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

    // configure transformation matrices
    val projection = new Matrix4f().perspective(
      Math.toRadians(45.0f).toFloat,
      SCR_WIDTH.toFloat / SCR_HEIGHT.toFloat,
      0.1f,
      1000.0f
    )
    val view = camera.getViewMatrix
    asteroidShader.use()
    asteroidShader.setMat4("projection", projection)
    asteroidShader.setMat4("view", view)
    planetShader.use()
    planetShader.setMat4("projection", projection)
    planetShader.setMat4("view", view)

    // draw planet
    val model = new Matrix4f().identity
      .translate(0.0f, -3.0f, 0.0f)
      .scale(4.0f, 4.0f, 4.0f)
    planetShader.setMat4("model", model)
    planet.draw(planetShader)

    // draw meteorites
    asteroidShader.use()
    asteroidShader.setInt("texture_diffuse1", 0)
    glActiveTexture(GL_TEXTURE0)
    glBindTexture(
      GL_TEXTURE_2D,
      rock.texturesLoaded(0).id
    ); // note: we also made the textures_loaded vector public (instead of private) from the model class.
    for (i <- 0 until rock.meshes.size) {
      glBindVertexArray(rock.meshes(i).vao)
      glDrawElementsInstanced(
        GL_TRIANGLES,
        rock.meshes(i).indices.size,
        GL_UNSIGNED_INT,
        0,
        amount
      )
      glBindVertexArray(0);
    }

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
